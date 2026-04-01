# Agent V2 Streaming Design

**Date:** 2026-04-01

**Goal:** Replace blocking LLM response handling with text streaming for all frontend-visible agent output while keeping tool execution deferred until the model response is complete.

## Scope

This change targets the `agent.v2` execution path only.

Add:
- streaming model invocation for agent decisions that produce frontend-visible text
- native execution events for text stream start, delta, and completion
- backend wiring so tool calls are collected during streaming but executed only after the final model response is available

Keep:
- existing tool execution runtime semantics
- existing `TOOL_CALLED`, `TOOL_SUCCEEDED`, and `TOOL_FAILED` event timing
- current task persistence and websocket fan-out infrastructure

Out of scope for the first pass:
- legacy `agent.v1` runtime changes
- speculative tool execution during partial tool-call streaming
- frontend protocol redesign beyond consuming new text-stream events

## Current Problem

All `agent.v2` tool-loop agents currently call LangChain4j through blocking `ChatModel` invocations and only return after a full `ChatResponse` is available. This means:

- user-visible text cannot reach the frontend until the model has completed
- tool calls and natural-language output are coupled inside a single blocking step
- the existing websocket event channel can only emit coarse runtime milestones, not incremental model text

The blocking pattern appears in:

- `ReactAgent`
- `ResearcherAgent`
- `GroundedWriterAgent`
- `EvidenceReviewerAgent`
- `ReflexionCritic`

Tool execution itself is already centralized in `ToolLoopExecutionRuntime`, which is a good boundary to preserve.

## Design Decisions

### 1. Stream text at the model-invocation boundary, not in the frontend

The backend must switch from `ChatModel` to `StreamingChatModel` for agent decisions that can produce frontend-visible text. Simulating streaming in the UI after a blocking model call would not improve latency and would not satisfy the requirement.

Recommended backend boundary:

- introduce a shared streaming invoker component for `agent.v2`
- let each tool-loop agent delegate model invocation to that component
- keep decision translation (`Complete`, `ToolCalls`, structured parsing) inside the agent layer

This avoids duplicating stream callback logic across five agent implementations.

### 2. Treat text tokens and tool calls as two separate channels

The required runtime semantics are:

- `onPartialResponse(...)`: publish text deltas to the frontend immediately
- `onPartialToolCall(...)`: do not execute anything
- `onCompleteToolCall(...)`: capture completed tool-call payloads only
- `onCompleteResponse(...)`: build the final `ChatResponse`, then decide whether to complete or execute tools

This preserves a clear separation:

- text output is optimistic and low-latency
- tool execution remains authoritative and only starts from a complete response

This is the key behavior change and should be treated as an invariant.

### 3. Keep tool execution in `ToolLoopExecutionRuntime`

The current runtime owns:

- allowed-tool validation
- `TOOL_CALLED` / `TOOL_SUCCEEDED` / `TOOL_FAILED` events
- memory updates for AI tool-call messages and tool results
- document-content mutation accumulation

That boundary should remain unchanged. Streaming should stop at “produce a final `ToolLoopDecision`”.

This means tool-loop agents should still return:

- `ToolLoopDecision.ToolCalls` after full response completion when tool requests exist
- `ToolLoopDecision.Complete` when the final response is textual or structured completion data

### 4. Add explicit text-stream execution events

`ExecutionEvent` currently only carries `type`, `taskId`, and `message`. That is enough for a first streaming version if the frontend only needs ordered text chunks and can reconstruct by task and event sequence.

Add event types:

- `TEXT_STREAM_STARTED`
- `TEXT_STREAM_DELTA`
- `TEXT_STREAM_COMPLETED`

Recommended semantics:

- `TEXT_STREAM_STARTED`: emitted once per streamed model invocation before the first text delta
- `TEXT_STREAM_DELTA`: emitted for each partial text chunk
- `TEXT_STREAM_COMPLETED`: emitted when the model response finishes, regardless of whether the same response also contains tool calls

The current websocket and query infrastructure can continue to transport `ExecutionEvent` without introducing a second realtime payload type.

### 5. Stream every frontend-visible text source in `agent.v2`

The user requirement is broader than plain ReAct final answers. Anything that the frontend currently surfaces as agent text should stream if it comes from a model response.

This includes:

- direct `REACT` agent responses
- supervisor worker responses that are displayed to the frontend
- reflexion critic/actor user-visible text
- any model-produced completion summary that is surfaced through current task events

It does not include:

- tool invocation notifications
- tool result messages
- synthetic runtime status strings such as `iteration 0`

### 6. Preserve structured-completion parsing after full aggregation

Several agents parse model text into structured payloads after the model call returns:

- `ResearcherAgent`
- `EvidenceReviewerAgent`
- `ReflexionCritic`

These agents should continue parsing only after the full streamed response has been assembled. Partial chunks must not be parsed incrementally.

### 7. Use feature-gated rollout through request intent only if needed

`AgentTaskRequest` already has a `streaming` flag that is currently unused. If rollout risk needs to be reduced, this flag can gate the new behavior so that:

- `streaming=true` uses the streaming model path
- `streaming=false` falls back to the current blocking path

If the implementation is clean and test coverage is strong, it is also acceptable to make streaming the default for v2 and keep the flag only for compatibility.

## Architecture

### Backend flow for a single streamed model invocation

1. Runtime starts an agent iteration as today.
2. The agent builds `ModelInvocationContext` as today.
3. The agent calls a shared streaming invoker with:
   - task id
   - agent identity or worker identity if needed for diagnostics
   - `ChatRequest`
4. The streaming invoker:
   - publishes `TEXT_STREAM_STARTED`
   - emits `TEXT_STREAM_DELTA` on each partial text chunk
   - buffers complete tool calls
   - waits for `onCompleteResponse`
5. The invoker returns the full `ChatResponse` plus captured complete tool calls.
6. The agent converts the completed response into `ToolLoopDecision`.
7. If tools were requested, `ToolLoopExecutionRuntime` executes them exactly as today and emits `TOOL_CALLED`.

### Suggested new component

Create a shared component such as:

- `agent/v2/model/StreamingDecisionInvoker`

Responsibilities:

- bridge `StreamingChatModel` callbacks into a synchronous completion primitive usable by existing runtimes
- publish text-stream events
- aggregate final response state and propagate errors

Non-responsibilities:

- parsing structured domain payloads
- executing tools
- mutating memory

## Event Contract

### Text streaming

The frontend should treat `TEXT_STREAM_DELTA` as append-only content for the current active model turn.

Important behavior:

- deltas may contain more than one token
- a response can legally emit text deltas and also finish with tool calls
- `TEXT_STREAM_COMPLETED` means “this model turn has finished streaming”, not “the whole task is complete”

### Tool events

Tool events remain unchanged:

- no `TOOL_CALLED` during partial tool-call streaming
- `TOOL_CALLED` only after the complete response has been received and the runtime begins actual tool execution

This preserves compatibility with existing task timelines and memory semantics.

## Error Handling

- streaming callback errors should fail the current task the same way blocking model-call failures do
- if text deltas were already emitted before failure, the frontend should keep them as partial output and then render the failure event
- if the provider emits malformed or incomplete tool-call data, the invocation should fail before any tool execution starts
- structured parsing failures after complete aggregation should keep current fallback behavior where applicable

## Risks

### 1. LangChain4j version compatibility

The repository currently pins `langchain4j` to `1.11.0`, while the referenced streaming documentation reflects the current documentation site. The implementation should first verify that the local API version supports the required streaming callbacks for:

- partial text
- partial or complete tool calls
- complete response completion

If not, a dependency upgrade becomes part of the change scope.

### 2. Provider-specific streaming behavior

The OpenAI-compatible backend may batch text chunks rather than emit single tokens, and mixed text/tool responses may differ by provider. The design should assume ordered chunk delivery but should not assume one-token granularity.

### 3. Frontend turn reconstruction

If the frontend currently assumes one event equals one display line, it will need a small stateful append model for streamed text. This is a UI integration concern, not a backend architecture blocker.

## Testing Strategy

Backend tests should cover:

- streaming text deltas are published before model completion
- tool calls are not executed until complete response aggregation finishes
- `TOOL_CALLED` still occurs in runtime order after full tool-call assembly
- structured-output agents still parse correctly from aggregated text
- supervisor worker flows also emit stream events for user-visible text

Integration tests should cover:

- websocket delivery of `TEXT_STREAM_*` events
- event replay consistency between realtime and stored task events
- fallback behavior when streaming is disabled or unsupported

## Verification Criteria

The change is complete when all of the following are true:

- frontend-visible model text appears incrementally without waiting for the full model response
- tool execution still starts only after a complete response is available
- `TOOL_CALLED` / `TOOL_SUCCEEDED` / `TOOL_FAILED` semantics remain unchanged
- structured agent outputs still parse correctly after stream aggregation
- supervisor mode also streams worker text that is exposed to the frontend

## Review Notes

- 文本流式和工具执行必须严格解耦，不能在 `onPartialToolCall` 阶段偷跑工具，否则会把 provider 的增量协议细节泄漏到 runtime 语义里。
- runtime 仍应保持“只消费完整 decision”的边界；流式复杂度应收敛在模型调用适配层，而不是扩散到工具执行和 memory 层。

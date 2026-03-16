# Agent V2 React Memory And AiService Design

**Date:** 2026-03-16

## Goal

Refactor the medium-priority `agent.v2` model integration to:

- move `ReactAgentDefinition` to an `AiService`-based model boundary
- introduce session-scoped `ChatMemory` for both `React` and `Supervisor`
- keep the existing custom runtime and orchestration model intact

This round does not introduce shared memory across workers and does not replace the custom execution loop.

## Why This Refactor

After the high-priority simplifications:

- `Planning` uses `AiService`
- `Supervisor` uses `AiService`
- `React` still calls `ChatModel` directly and hand-builds prompt messages
- `React` and `Supervisor` both still rebuild most context explicitly every iteration

That leaves two gaps:

1. the model integration style is inconsistent across `agent.v2`
2. multi-turn context still relies too heavily on handwritten prompt assembly instead of session-scoped memory

## Chosen Approach

Use `AiService` and `ChatMemory` at the model boundary, but keep runtime state and orchestration state custom.

- add a `ReactAiService`
- change `ReactAgentDefinition` into an adapter from runtime state to `AiService`
- add a session-scoped memory provider
- wire `React` and `Supervisor` `AiServices` to that memory provider
- keep `ExecutionState.currentContent` as the authoritative document state

This gives the system a consistent langchain4j integration model without surrendering the application's execution semantics.

## Design

### 1. React AiService

Add a `ReactAiService` interface whose responsibility is limited to one model decision turn.

`ReactAgentDefinition` will stop calling `ChatModel.chat(...)` directly.

Instead it will:

- derive the current document and instruction input from `ExecutionContext`
- pass the current tool result summary as structured prompt input
- invoke `ReactAiService`
- translate the model result into the existing `Decision`

The runtime contract remains unchanged:

- `Decision.ToolCalls`
- `Decision.Complete`
- `ToolCall`

This means `DefaultExecutionRuntime` still owns the loop and tool execution.

### 2. ChatMemory Boundaries

`ChatMemory` is introduced for message history, not for source-of-truth document state.

The authoritative document content remains:

- `ExecutionState.currentContent`

The memory only stores conversational history for the model layer:

- previous user/assistant turns
- prior tool observations as model-visible history
- prior routing context for supervisor decisions

This avoids the failure mode where memory drifts away from the actual document state.

### 3. Memory Scope Keys

Memory keys are explicit and mode-specific.

Recommended key rules:

- plain ReAct task: `sessionId`
- worker execution: `sessionId:workerId`
- supervisor routing: `sessionId:supervisor`

This allows:

- a standalone ReAct session to keep its own history
- each worker to retain its own local reasoning history
- the supervisor to retain routing history

And it avoids:

- worker-to-worker memory contamination
- supervisor history leaking into worker tool decisions

### 4. Supervisor Memory

`HybridSupervisorAgentDefinition` keeps the current rule-based candidate filtering and fallback behavior.

This refactor only changes the model boundary:

- wire `SupervisorRoutingAiService` to `ChatMemory`
- keep explicit candidate and worker summary rendering
- preserve invalid-output fallback
- preserve no-progress termination logic

The supervisor should still be explainable from explicit state, even after memory is added.

### 5. Configuration

Introduce langchain4j memory wiring in configuration.

Expected additions:

- `langchain4j-chat-memory` dependency
- a `ChatMemoryProvider` bean or equivalent session-scoped memory factory
- `ReactAgentConfig` uses `AiServices.builder(...)` for `ReactAiService`
- `SupervisorAgentDefinition` construction receives the memory-enabled `AiService`

Memory configuration should be intentionally bounded, for example by a message window limit, so history does not grow without control.

## Data Flow

### React

`DefaultExecutionRuntime`
-> `ReactAgentDefinition`
-> derive memory key from `sessionId` and optional `workerId`
-> `ReactAiService`
-> model output
-> `Decision`

`ExecutionState.currentContent` remains the actual document state used by the runtime after each tool call.

### Supervisor

`SupervisorOrchestrator`
-> `HybridSupervisorAgentDefinition`
-> candidate filtering
-> supervisor memory key `sessionId:supervisor`
-> `SupervisorRoutingAiService`
-> `SupervisorRoutingResponse`
-> `SupervisorDecision`

## Error Handling

- If `ReactAiService` fails, `ReactAgentDefinition` should fall back to a safe completion path rather than crash the runtime.
- If memory is unavailable or misconfigured, the model boundary should degrade to stateless operation where possible.
- `Supervisor` keeps its current rule-based fallback on invalid or failed model responses.
- Memory introduction must not remove any existing loop guard, fallback, or allow-list enforcement.

## Testing Strategy

Update or add tests for:

- `ReactAgentDefinitionTest`
- `HybridSupervisorAgentDefinitionTest`
- configuration tests for memory-aware wiring

Test focus:

- `React` still maps tool calls and completions correctly after `AiService` migration
- memory scope keys differ correctly for plain ReAct, worker execution, and supervisor routing
- supervisor fallback behavior remains unchanged with memory enabled
- no runtime state is read back from memory as the source of document truth

Regression verification should include:

- targeted `React` and `Supervisor` tests
- orchestrator regression tests
- full `mvn test`

## Risks

### 1. Memory Scope Leakage

If memory keys are too broad, worker and supervisor state will contaminate each other. This must be prevented at the configuration boundary.

### 2. Reduced Observability

`AiService` can hide low-level request construction details that `ReactAgentDefinition` currently traces. The implementation must preserve enough trace payload to keep model behavior debuggable.

### 3. False Source Of Truth

If future code starts treating `ChatMemory` as the document state, runtime behavior will become nondeterministic. The design explicitly forbids that.

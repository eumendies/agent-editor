# Agent V2 Recoverable Tool Errors Design

## Goal

Prevent `agent.v2` tool-loop executions from breaking the chain when the model makes a recoverable tool mistake. Parameter parsing errors, invalid business arguments, missing or disallowed tools, and similar model-correctable failures should be returned to the model as tool-result messages instead of being thrown out of the runtime.

## Scope

In scope:

- introduce a dedicated recoverable tool exception type in `agent.v2`
- move recoverable error-to-message conversion into `ToolLoopExecutionRuntime`
- convert document and memory tools from "catch and return `ToolResult`" to "throw typed recoverable exceptions"
- treat unknown or disallowed tools as recoverable tool results inside the runtime
- add runtime-focused tests that prove the loop continues after recoverable failures
- adjust tool tests to reflect the new exception boundary

Out of scope:

- changing supervisor/planning runtime invariants
- swallowing infrastructure failures such as serialization bugs or service outages
- redesigning all `ToolResult` success payloads
- changing websocket failure semantics for non-recoverable runtime crashes

## Current Problem

`agent.v2` currently mixes two different failure strategies:

- some tools catch `IllegalArgumentException` and immediately return structured JSON errors
- other tools still let parameter parsing or validation exceptions escape
- `ToolLoopExecutionRuntime` throws for missing/disallowed tools before the model can react

That inconsistency means the same class of model mistakes sometimes produces a tool result and sometimes aborts the entire run. The chain breaks exactly in the cases where the model should be able to inspect the failure and retry with corrected arguments.

## Recommended Approach

Introduce `RecoverableToolException` and make `ToolLoopExecutionRuntime` the single place that converts recoverable tool failures into structured error `ToolResult` messages.

Why this approach:

- it gives one consistent recovery boundary for all tool calls
- it prevents future tools from re-implementing bespoke catch-and-serialize logic
- it keeps true system failures visible by limiting recovery to explicitly marked exceptions
- it lets runtime-owned failures such as "tool missing" and "tool not allowed" follow the same contract as tool-owned validation errors

## Alternatives Considered

### 1. Keep tool-local error serialization

Catch and serialize recoverable failures inside each tool.

Pros:

- small localized changes
- little runtime churn

Cons:

- easy to miss new tools
- cannot handle missing/disallowed tool calls centrally
- keeps the failure contract fragmented

### 2. Runtime catches broad `IllegalArgumentException`

Treat every `IllegalArgumentException` thrown during tool execution as recoverable.

Pros:

- smaller refactor than introducing a new exception type

Cons:

- `IllegalArgumentException` is too broad and can hide programming errors
- the recovery boundary becomes ambiguous and fragile

### 3. Dedicated recoverable exception plus runtime conversion

Make recoverable tool errors explicit and handle them centrally.

Pros:

- clear semantics
- complete runtime coverage
- preserves fail-fast behavior for system faults

Cons:

- requires touching both runtime and affected tools

## Exception Boundary

Use two categories:

- Recoverable failures:
  - malformed tool arguments
  - unsupported enum or mode values chosen by the model
  - domain validation failures such as unknown `nodeId`, invalid patch operation, or disallowed autonomous memory writes
  - unknown/disallowed tool names requested by the model
- Non-recoverable failures:
  - result serialization failures
  - unexpected service/runtime exceptions
  - runtime invariant violations

Only recoverable failures are converted into tool-result messages.

## Runtime Behavior

`ToolLoopExecutionRuntime.executeTools(...)` should:

1. publish `TOOL_CALLED`
2. resolve the handler
3. if the tool is missing or disallowed, create a recoverable failure result instead of throwing
4. execute the handler
5. if the handler throws `RecoverableToolException`, convert it into a structured error result
6. if the handler throws any other exception, rethrow and fail the task
7. append the resulting tool message to memory and continue the loop

这样可以保证模型下一轮总能看到可修复错误，而不会因为 runtime 提前抛异常而失去自纠机会。

## Error Message Contract

Recoverable failures returned by the runtime should use a stable JSON envelope in `ToolResult.message`:

```json
{
  "status": "error",
  "tool": "patchDocumentNode",
  "errorMessage": "Unknown nodeId: node-999",
  "arguments": "{\"documentVersion\":\"v1\", ...}"
}
```

Fields:

- `status`: always `"error"`
- `tool`: tool name from the call
- `errorMessage`: user/actionable validation message
- `arguments`: original tool-call argument JSON string

The runtime should not mutate `updatedContent` on recoverable failure.

## Tool Changes

Affected tools should stop returning recoverable error `ToolResult` objects directly.

Instead they should:

- throw `RecoverableToolException` for argument decoding failures
- wrap domain `IllegalArgumentException` into `RecoverableToolException`
- keep success payloads unchanged
- continue throwing unrecoverable failures such as serialization problems

Initial targets:

- `ToolArgumentDecoder`
- `ReadDocumentNodeTool`
- `PatchDocumentNodeTool`
- `MemorySearchTool`
- `MemoryUpsertTool`
- any simple document tools that currently rely on decoder exceptions bubbling out

## Testing Strategy

### Runtime tests

Add focused tests in `ToolLoopExecutionRuntimeTest` for:

- missing/disallowed tool does not throw and instead appears as a tool result the agent can inspect
- handler-thrown `RecoverableToolException` does not break the loop
- ordinary `RuntimeException` still aborts execution

### Tool tests

Adjust tool-layer tests so they assert the new exception boundary:

- invalid JSON now throws `RecoverableToolException`
- recoverable service validation now throws `RecoverableToolException`
- success behavior remains unchanged

Runtime tests become the primary place that verifies JSON error envelopes are produced.

## Risks And Mitigations

- Risk: accidentally swallowing system faults.
  Mitigation: recover only `RecoverableToolException` and explicitly classified runtime-owned failures.

- Risk: tool tests lose signal after moving formatting into runtime.
  Mitigation: keep tool tests focused on exception typing and keep runtime tests focused on serialized envelopes.

- Risk: prompts or downstream code depend on old tool-specific error payload shapes.
  Mitigation: use a stable generic envelope and verify the loop memory still receives a clear actionable `errorMessage`.

## Success Criteria

- model-correctable tool failures no longer abort `agent.v2` tool loops
- the next model iteration can inspect a structured error result and retry
- runtime-owned tool resolution failures follow the same recoverable contract
- non-recoverable failures still surface and fail loudly
- tests lock down both the exception boundary and the runtime recovery behavior

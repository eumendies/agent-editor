# Document Tool Error Envelope Design

## Goal

Prevent `readDocumentNode` and `patchDocumentNode` from aborting the agent runtime when `StructuredDocumentService` rejects an invalid request. Instead of propagating recoverable tool failures as exceptions, return structured JSON error payloads so the model can adjust its next action.

## Scope

In scope:

- catch recoverable `StructuredDocumentService` failures inside `ReadDocumentNodeTool`
- catch recoverable `StructuredDocumentService` failures inside `PatchDocumentNodeTool`
- keep successful tool responses unchanged
- add focused tool-layer tests that lock down the new failure contract

Out of scope:

- changing `StructuredDocumentService` public method signatures
- redesigning the global `ToolResult` type
- introducing a new cross-runtime exception hierarchy
- changing unrelated whole-document tools

## Current Problem

`StructuredDocumentService` already models one failure path as data by returning `PatchResult.baselineMismatch(...)`, but several other invalid inputs still throw `IllegalArgumentException`, including:

- unknown `nodeId`
- unknown `blockId`
- unsupported read `mode`
- unsupported patch `operation`
- invalid `replace_node` replacement content

`ReadDocumentNodeTool` and `PatchDocumentNodeTool` currently call the service directly and let those exceptions escape. When that happens, the tool loop treats the invocation as a runtime failure instead of a recoverable tool response, so the model loses the chance to inspect the error and self-correct.

## Recommended Approach

Catch recoverable validation exceptions inside each tool and serialize them into a shared JSON error envelope returned through `ToolResult.message`.

Why this approach:

- it solves the runtime-stability problem at the actual failure boundary
- it keeps `StructuredDocumentService` focused on document semantics instead of transport formatting
- it preserves the existing success payloads, minimizing prompt and caller churn
- it avoids a larger refactor of service return types for a narrow, well-defined bug

## Alternatives Considered

### 1. Only tool-layer exception capture

Catch service exceptions in `ReadDocumentNodeTool` and `PatchDocumentNodeTool`, then return structured error JSON.

Pros:

- smallest safe change
- no service API churn
- directly aligns with the requirement that the model should receive the error

Cons:

- future error payload shape changes still require touching both tools

### 2. Convert all service failures into result objects

Make `StructuredDocumentService.readNode(...)` and `applyPatch(...)` return success-or-error result envelopes instead of throwing.

Pros:

- one consistent service contract

Cons:

- broad API change
- larger regression surface
- unnecessary for the immediate runtime problem

### 3. Add a dedicated document tool exception hierarchy

Introduce typed domain exceptions such as `DocumentToolException`, then catch and serialize them in the tools.

Pros:

- clearer semantics than raw `IllegalArgumentException`

Cons:

- new abstraction without immediate leverage
- still requires tool-layer serialization work

## Error Response Contract

Both tools should return JSON on recoverable failure through `ToolResult.message`.

Shared fields:

- `status`: always `"error"`
- `errorMessage`: human-readable description derived from the underlying validation error
- `nodeId`: the requested node id when available
- `blockId`: the requested block id when available
- `operation`: patch operation for patch-tool failures, otherwise `null`

Example:

```json
{
  "status": "error",
  "errorMessage": "Unknown nodeId: node-999",
  "nodeId": "node-999",
  "blockId": null,
  "operation": null
}
```

Success responses stay unchanged:

- `ReadDocumentNodeTool` still returns the current `NodeReadResult` JSON
- `PatchDocumentNodeTool` still returns the current success JSON and updated content

## Exception Boundary

Wrap the service invocation path inside each tool:

- decode arguments
- call the service in a `try/catch`
- on `IllegalArgumentException`, return the JSON error envelope
- on success, keep the existing behavior

Deliberately do not swallow:

- JSON serialization failures
- impossible internal failures such as `IllegalStateException`

These are programming errors rather than model-recoverable input mistakes and should still surface loudly.

## Tool-Specific Behavior

### `ReadDocumentNodeTool`

- success: unchanged `NodeReadResult` JSON
- recoverable failure: return error JSON with `operation = null`
- `updatedContent`: always `null`

### `PatchDocumentNodeTool`

- success: unchanged success JSON plus updated content
- recoverable failure: return error JSON with requested `operation`
- `updatedContent`: must remain `null` on failure so invalid patches never overwrite document state

## Testing Strategy

Add focused tool tests rather than changing existing service tests. The service should continue throwing on invalid calls; the new behavior belongs to the tool boundary.

### `ReadDocumentNodeToolTest`

Add cases for:

- unknown `nodeId`
- unsupported `mode`
- unknown `blockId`

Each case should assert:

- the tool does not throw
- `ToolResult.message` is JSON with `status = error`
- the original validation `errorMessage` is present
- `updatedContent` is `null`

### `PatchDocumentNodeToolTest`

Add cases for:

- unknown `nodeId`
- unsupported `operation`
- invalid `replace_node` replacement body

Each case should assert:

- the tool does not throw
- `ToolResult.message` is JSON with `status = error`
- the original validation `errorMessage` is present
- `updatedContent` is `null`

## Risks And Mitigations

- Risk: a tool accidentally swallows programming errors and hides real bugs.
  Mitigation: only catch `IllegalArgumentException`, leaving serialization and internal-state failures uncaught.

- Risk: patch failures accidentally include stale `updatedContent`.
  Mitigation: construct failure `ToolResult` instances with message only and assert this in tests.

## Success Criteria

- Invalid `readDocumentNode` and `patchDocumentNode` requests no longer fail the agent runtime through uncaught recoverable exceptions.
- The model receives a JSON error payload with actionable `errorMessage` and request context.
- Successful tool responses stay backward-compatible.
- Focused tests lock down the new failure contract.

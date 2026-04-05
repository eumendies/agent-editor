# Memory Tool Error Envelope Design

## Goal

Prevent `memory_upsert` and `memory_search` from aborting the agent runtime when recoverable memory-tool validation errors occur. Instead of propagating those errors as exceptions, return structured JSON error payloads so the model can inspect the failure and adjust the next tool call.

## Scope

In scope:

- catch recoverable `IllegalArgumentException` failures inside `MemoryUpsertTool`
- catch recoverable `IllegalArgumentException` failures inside `MemorySearchTool`
- keep successful tool responses unchanged
- add focused tool-layer tests for the new failure contract

Out of scope:

- changing `LongTermMemoryWriteService` or `LongTermMemoryRetrievalService` public signatures
- redesigning the global `ToolResult` type
- refactoring all tool classes to a shared error-envelope abstraction
- changing unrelated document or knowledge tools

## Current Problem

The two memory tools currently wrap execution failures by throwing `IllegalArgumentException`:

- `MemoryUpsertTool` throws `Failed to execute tool memory_upsert`
- `MemorySearchTool` throws `Failed to serialize tool result for memory_search`

That behavior is inappropriate for recoverable input and validation failures:

- invalid `action` enum values
- invalid `memoryType` enum values
- missing `memoryId` / `summary` / `documentId`
- mismatched document decision replacement requests
- retrieval-service validation failures surfaced as `IllegalArgumentException`

When those exceptions escape, the runtime treats the tool call as a failed execution instead of a recoverable tool response, so the model loses the opportunity to self-correct.

## Recommended Approach

Mirror the current document-tool contract used by `ReadDocumentNodeTool` and `PatchDocumentNodeTool`: catch recoverable `IllegalArgumentException` failures inside each memory tool and return JSON through `ToolResult.message`.

Why this approach:

- it solves the runtime-stability problem at the tool boundary
- it stays consistent with existing agent-v2 tool behavior
- it preserves current success payloads
- it avoids unnecessary service-layer API churn

## Error Response Contract

Both memory tools should return a JSON payload like:

```json
{
  "status": "error",
  "errorMessage": "memoryId is required for replace/delete"
}
```

The payload should also include request context when it helps the model repair the next call.

### `MemoryUpsertTool`

Fields:

- `status`: always `"error"`
- `errorMessage`: the original validation message
- `action`: requested action when available
- `memoryType`: requested memory type when available
- `memoryId`: requested memory id when available
- `documentId`: requested document id when available

### `MemorySearchTool`

Fields:

- `status`: always `"error"`
- `errorMessage`: the original validation message
- `query`: requested query when available
- `documentId`: requested document scope when available
- `topK`: requested limit when available

## Exception Boundary

Catch only `IllegalArgumentException` inside `execute(...)`.

This covers recoverable errors from:

- `MemoryUpsertAction.valueOf(...)`
- `LongTermMemoryType.valueOf(...)` inside `LongTermMemoryWriteService`
- service-layer input validation

Do not swallow:

- JSON serialization failures
- `IllegalStateException` such as missing memory infrastructure
- other unexpected runtime failures

These remain programming or infrastructure errors and should still surface loudly.

## Parse Failure Decision

Keep malformed JSON argument parsing behavior unchanged for now.

Reasoning:

- the existing document tools still throw on malformed JSON arguments
- this change request is specifically about execution-time validation failures
- keeping parse failures unchanged minimizes cross-tool behavioral drift

## Testing Strategy

Add focused tests to the memory-tool test classes.

### `MemoryUpsertToolTest`

Add cases for:

- service validation failure such as missing `memoryId` for `REPLACE`
- invalid `action` enum value

Assertions:

- the tool does not throw
- `ToolResult.message` is JSON with `status = error`
- `errorMessage` preserves the original validation message
- `updatedContent` is `null`

### `MemorySearchToolTest`

Add a case where the retrieval service throws `IllegalArgumentException`.

Assertions:

- the tool does not throw
- `ToolResult.message` is JSON with `status = error`
- `errorMessage` preserves the original validation message
- `updatedContent` is `null`

## Risks And Mitigations

- Risk: swallowing infrastructure failures hides real bugs.
  Mitigation: only catch `IllegalArgumentException`.

- Risk: memory tools drift from document-tool conventions.
  Mitigation: reuse the same `status/errorMessage` envelope shape instead of inventing a new contract.

## Success Criteria

- recoverable memory-tool validation failures no longer break the agent runtime through uncaught exceptions
- the model receives structured JSON error details it can use for self-correction
- success responses remain backward-compatible
- focused tests lock down the new contract

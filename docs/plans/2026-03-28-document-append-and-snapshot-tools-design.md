# Document Append And Snapshot Tools Design

**Goal:** Add two new document tools so agents can append content to the end of the current document without overwriting it, and can explicitly fetch the latest in-loop document snapshot.

## Problem

The current tool set only supports:

- `editDocument` for full overwrite
- `searchContent` for text lookup
- `analyzeDocument` for lightweight stats/inspection

That leaves two gaps:

1. Agents cannot express "append this exact content to the end" without reconstructing the full document and calling `editDocument`.
2. Agents cannot explicitly ask the runtime for the latest document content after one or more tool calls in the same loop.

In practice this causes prompt ambiguity:

- simple append tasks are heavier than necessary
- agents may overuse overwrite semantics
- multi-step agents have no explicit "read current draft snapshot" tool contract

## Chosen Design

Add two new `ToolHandler` implementations under the existing document-tool package:

- `appendToDocument`
- `getDocumentSnapshot`

Both tools reuse the existing runtime contract:

- input comes from `ToolInvocation.arguments()`
- latest content comes from `ToolContext.currentContent`
- write tools return `ToolResult(message, updatedContent)`
- read-only tools return `ToolResult(message)` and do not mutate content

No runtime protocol changes are needed.

## Tool Contracts

### `appendToDocument`

**Purpose:** Append raw text to the end of the current document.

**Arguments:**

- `content` (required)

**Behavior:**

- read the current document from `ToolContext.currentContent`
- append `content` exactly as provided
- do not auto-insert newline
- if current content is `null`, treat it as empty string
- if `content` is missing or blank, return a non-updating failure-style message consistent with existing document tool behavior

**Result:**

- `message`: append succeeded / append skipped
- `updatedContent`: appended full document on success

### `getDocumentSnapshot`

**Purpose:** Return the latest document content visible to the current tool loop iteration chain.

**Arguments:**

- none required

**Behavior:**

- read `ToolContext.currentContent`
- return it as the tool message payload
- do not modify document content

**Result:**

- `message`: current snapshot text
- `updatedContent`: `null`

## Naming

Confirmed names:

- `appendToDocument`
- `getDocumentSnapshot`

## Why Separate Tools

This is better than extending `editDocument` with modes because:

- overwrite vs append remain explicit and model-facing
- existing `editDocument` tests and prompt semantics stay stable
- agent prompts can explain the distinction clearly
- tool-call traces remain easy to interpret

## Prompt / Tool Visibility Updates

Agents that currently edit documents should see the new tools where appropriate.

Minimum update:

- ReAct-style writer path
- Reflexion actor path through shared React context
- grounded writer / editor-style supervisor worker paths if they already consume document editing tools

Prompt guidance should say:

- use `editDocument` for full replacement / rewrite
- use `appendToDocument` when only appending to the end
- use `getDocumentSnapshot` when you need the latest full content after prior tool effects

## Runtime Impact

No new runtime branch is required.

`ToolLoopExecutionRuntime` already supports:

- sequential tool execution within a loop
- content mutation through `ToolResult.updatedContent`
- carrying latest content into later tool invocations

That means `getDocumentSnapshot` naturally sees the latest in-loop content, including results produced by earlier tool calls in the same turn.

## Testing Strategy

Add focused tests for:

- `appendToDocument` appends exact raw text
- `appendToDocument` treats null current content as empty
- `appendToDocument` rejects missing content consistently
- `getDocumentSnapshot` returns current content and does not mutate it
- `ToolConfig` / registry exposes the new tools
- agent-visible tool lists and prompts include the new tools where required
- runtime path proves `getDocumentSnapshot` can observe content after a prior append/edit tool call in the same tool loop

## Non-Goals

This change does not:

- add positional insert/edit tools
- add structured patch/diff tools
- auto-normalize whitespace or newline handling for append
- redesign the document tool abstraction

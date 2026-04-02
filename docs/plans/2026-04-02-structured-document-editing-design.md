# Structured Document Editing Design

## Goal

Prevent long Markdown documents from overflowing the model context during agent tasks. The agent should initialize from a structural document snapshot instead of the full document body, read sections on demand, and edit incrementally. When a leaf section still exceeds the safe context budget, the runtime should temporarily degrade that leaf into stable content blocks without changing the user's chapter-oriented editing mental model.

## Scope

In scope:

- replace full-document initialization with structure-first initialization for long-document editing tasks
- add two document tools for node-level reads and node-level patch writes
- reuse Markdown AST structure to address chapter-oriented access
- introduce a runtime-only leaf-block fallback for oversized leaf sections
- preserve the existing diff-card review flow by still producing a final full-document candidate
- validate task-internal edit baselines so patches are not applied against stale node or block content

Out of scope:

- automatic merge or rebase when the saved editor document changes before the user applies a diff card
- changing the frontend to expose block-level UI concepts
- global automatic rewrites that span many oversized sections in a single step
- replacing the current diff-card confirmation flow

## Current Problem

The current `agent/v2` runtime treats `currentContent` as the whole document body. `getDocumentSnapshot` returns the full current content, and `editDocument` overwrites the full content. This creates two problems for long Markdown documents:

- task initialization can exceed the model context before the agent even starts tool use
- any meaningful edit requires the model to hold or regenerate the whole document, which scales poorly as the document grows

The repository already has a Markdown AST-based section tree builder and recursive section chunking logic for knowledge ingestion, but that structure is not available to the editing runtime or editing tools.

## Recommended Approach

Adopt a structure-first document editing model:

- initialize the agent with a `DocumentStructureSnapshot` instead of the full document body
- let the model inspect the chapter tree first, then read only the node it needs
- keep chapter-level editing as the default interaction model
- if a leaf node is still too large, temporarily degrade it into runtime-only blocks and require block-level reads and patches for that leaf
- let the server own full-document reconstruction after every accepted patch

Why this approach:

- it matches the existing Markdown editor mental model better than turning the entire system into generic chunks
- it avoids forcing the model to regenerate whole-document content for localized edits
- it can reuse the existing `MarkdownSectionTreeBuilder` direction instead of introducing a second unrelated parsing model
- it preserves the current diff-card application flow because the backend can still reconstruct a full candidate document after incremental edits

## Key Decisions

- Use Markdown structure as the primary editing address space.
- Keep the chapter as the default unit of reasoning and editing.
- Degrade only oversized leaf sections into blocks; do not expose blocks in the frontend UI.
- Replace runtime “conflict handling” terminology with “baseline validation” to reflect the real need: prevent task-internal stale node and stale block writes.
- Do not implement final apply-time merge checks in the first version because the current product flow already gates persistence behind a diff card.

## Document State Model

### DocumentStructureSnapshot

The runtime-visible structure object for one document version.

Suggested fields:

- `documentId`
- `documentVersion`
- `title`
- `nodeTree`
- `estimatedTokens`
- `oversizedNodeCount`

Each node in `nodeTree` should include:

- `nodeId`
- `path`
- `headingText`
- `headingLine`
- `headingLevel`
- `childCount`
- `charLength`
- `estimatedTokens`
- `leaf`
- `overflow`

The initial model context should contain this structure summary, not the full body text.

### DocumentNodeRef

Stable runtime handle for a chapter node.

Suggested fields:

- `nodeId`
- `path`
- `documentVersion`

`nodeId` is the canonical machine reference. `path` remains useful for traces, debugging, and model readability.

### LeafBlockRef

Runtime-only block descriptor for an oversized leaf node.

Suggested fields:

- `nodeId`
- `blockId`
- `ordinal`
- `startOffset`
- `endOffset`
- `charLength`
- `estimatedTokens`
- `hash`
- `summary`

Blocks should only appear when a leaf node exceeds the safe per-read threshold.

### DocumentPatch

Incremental write description submitted by the model.

Suggested fields:

- `documentVersion`
- `nodeId`
- optional `blockId`
- `baseHash`
- `operation`
- `content`
- optional `reason`

The backend remains responsible for patch application, AST update, and full-document reconstruction.

## Tool Design

### `readDocumentNode`

Purpose:

- read structure or content for a specific chapter node
- expose block metadata for oversized leaf nodes without dumping full oversized text into the model context

Suggested modes:

- `structure`: return node metadata only
- `content`: return heading line, direct body text, and child summaries for a normal-sized node
- `blocks`: return block descriptors for an oversized leaf

Suggested arguments:

- `nodeId`
- `mode`
- optional `blockId`
- optional `includeChildren`

Expected behavior:

- for normal nodes, `content` returns chapter-local editable material
- for oversized leaf nodes, `content` without `blockId` should not return the whole leaf body; it should instruct the model to request blocks
- for oversized leaf nodes, `blocks` returns stable block metadata first, and `content` with `blockId` returns one specific block body

### `patchDocumentNode`

Purpose:

- submit incremental edits at node or block granularity
- avoid full-document overwrite as the primary editing path

Suggested arguments:

- `documentVersion`
- `nodeId`
- optional `blockId`
- `baseHash`
- `operation`
- `content`
- optional `reason`

Suggested operations for v1:

- `replace_node`
- `replace_block`
- `insert_after_block`
- `delete_block`

Expected behavior:

- validate the provided baseline before applying the patch
- apply the patch to the targeted node or block
- rebuild affected block metadata if a leaf block layout changed
- reconstruct the full Markdown document after each successful patch
- return:
  - success or failure status
  - new `documentVersion`
  - updated target hash
  - concise mutation summary
  - if needed, a re-read instruction when baseline validation fails

## Oversized Leaf Fallback

The leaf fallback exists to handle the extreme case where one Markdown heading section still exceeds the safe model context size.

Rules:

- chapter nodes remain the primary addressing model
- only leaf chapters can degrade into blocks
- block boundaries should prefer paragraph boundaries and avoid mid-sentence cuts
- a block should target a conservative context budget instead of maximizing the available window
- adjacent blocks may include a small overlap to reduce continuity loss around local rewrites
- block identifiers should remain as stable as possible while the leaf structure remains materially unchanged

Suggested block sizing policy:

- target roughly 20% to 30% of the safe per-call model budget
- reserve headroom for system prompt, tool schema, user instruction, and intermediate reasoning

Additional guardrail:

- the model should not patch a block it has not read in the current task state

## Runtime Flow

### Task Initialization

1. Parse the Markdown document into a section tree.
2. Build a `DocumentStructureSnapshot`.
3. Initialize the task with the structural snapshot instead of full `currentContent`.
4. Update agent prompts so the model is instructed to:
   - inspect structure first
   - read only the target node or block
   - patch incrementally
   - avoid full-document overwrite unless explicitly routed through a small-document fallback

### Normal Chapter Edit Flow

1. The model locates the relevant chapter in the structure snapshot.
2. The model calls `readDocumentNode(content)` for that node.
3. The model submits `patchDocumentNode(replace_node)` with baseline data.
4. The backend reconstructs the full document candidate.

### Oversized Leaf Edit Flow

1. The model identifies an oversized leaf node from the structure snapshot.
2. The model calls `readDocumentNode(blocks)` to inspect available blocks.
3. The model reads one or more blocks by `blockId`.
4. The model submits block-level patch operations.
5. The backend updates the leaf, refreshes block metadata if needed, and reconstructs the full document candidate.

### Final Output

Even though the runtime edits incrementally, the backend should still produce a final full-document candidate so the existing diff-card review flow can remain the product confirmation surface.

## Baseline Validation

The first version should keep only task-internal baseline validation. This is necessary even when final persistence is deferred behind a diff card.

Validation is required for two cases:

- stale block or stale node writes after earlier task steps already changed the target content
- structure invalidation after a patch changed chapter hierarchy, node boundaries, or block boundaries

Validation behavior:

- every patch must carry `documentVersion` and `baseHash`
- if the target node or block no longer matches the expected baseline, reject the patch
- return a structured failure that tells the model to re-read the target node or block

This design intentionally does not implement final apply-time merge checks against the latest saved editor content in v1.

## Compatibility And Migration

Roll out in two stages.

### Stage 1: Parallel Path

- add the new structure-first path and tools
- keep `getDocumentSnapshot` and `editDocument` available as legacy or small-document fallbacks
- route new long-document editing prompts and orchestration toward `readDocumentNode` and `patchDocumentNode`

### Stage 2: Narrow Legacy Usage

- once the structure-first path is stable, reduce whole-document read and whole-document overwrite to debugging, compatibility, or explicit small-document scenarios

This phased migration reduces risk across the existing supervisor, planning, and React-style runtimes.

## Backend Impact

Expected change areas:

- `agent/v2/tool/document`
  - add `readDocumentNode`
  - add `patchDocumentNode`
  - add argument and result DTOs
- `agent/v2/tool/ToolContext`
  - stop assuming full-document `currentContent` is the primary runtime payload
  - make structure snapshot and version-aware document access available to tools
- context factories under `agent/v2`
  - initialize editing tasks from structure summaries rather than full bodies
- document service layer
  - add structure building
  - add node reads
  - add oversized-leaf block generation
  - add patch application and Markdown reconstruction
- diff generation path
  - keep producing a final full-document candidate for review

## Testing Strategy

### Structure And Patch Engine

- Markdown structure snapshots preserve heading hierarchy and source order
- oversized leaf nodes split into stable paragraph-oriented blocks
- block fallback does not lose text
- node-level and block-level patches reconstruct valid Markdown
- structure changes invalidate stale node or block baselines correctly

### Tool Layer

- `readDocumentNode` returns correct payloads for `structure`, `content`, and `blocks`
- oversized leaf reads require block resolution instead of returning the whole leaf body
- `patchDocumentNode` supports the allowed operations and returns updated versions and hashes
- baseline validation failures return structured re-read instructions

### Runtime Layer

- initial long-document context contains structure summary instead of full body content
- normal chapters follow node-level read and patch flow
- oversized leaf chapters follow block-level read and patch flow
- final task result still contains a full candidate document for diff-card review

## Non-Goals

- no apply-time three-way merge against the live editor document in v1
- no frontend block visualization
- no attempt to let the model rewrite an arbitrarily large chapter in one response
- no global consistency engine for cross-section rewrites in this iteration

## Open Follow-Up

Implementation still needs explicit planning around:

- exact safe token thresholds for structure reads, node reads, and block reads
- whether block patch operations should store raw replacement content only or also support richer textual patch formats later
- how to expose small-document fallback logic cleanly without confusing the model prompt

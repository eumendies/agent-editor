# Markdown Recursive Chunking Design

## Goal

Improve knowledge-base ingestion for Markdown files so chunking preserves document structure. The splitter should first chunk by sections, recursively descend into smaller subsections when a section exceeds `rag.chunk-size`, and only fall back to sliding-window text chunking when the smallest section still exceeds the size limit.

## Decisions

- Add a dedicated Markdown parser dependency and build the recursive chunking flow from an AST instead of heading-only regex splitting.
- Use `flexmark-java` as the Markdown parser because it provides a complete AST and leaves room for later handling of lists, code blocks, block quotes, and tables without reworking the splitter contract.
- Restrict AST-based recursive chunking to Markdown inputs. Non-Markdown knowledge documents keep the existing plain-text behavior.
- Promote `KnowledgeChunk.heading` from a single heading label to a full heading path for Markdown chunks, such as `项目经历 > Agent Editor > 检索增强`.
- Preserve heading text inside each chunk body so embeddings keep the local section context.
- Avoid parent-child duplication. If a parent section is too large and gets split into child sections, do not also emit a full parent chunk.
- Enforce `rag.chunk-size` as a hard upper bound. When a leaf section still exceeds the limit, use sliding-window text chunking with `rag.chunk-overlap` as the final fallback.

## Current Gap

`KnowledgeChunkSplitter` currently splits Markdown by a regular expression that only recognizes level-1 headings and truncates oversized sections with `substring(0, chunkSize)`. That drops content and cannot preserve nested Markdown structure.

## Proposed Components

- `KnowledgeChunkSplitter`: remains the public entry point and routes Markdown inputs into the AST-driven recursive flow.
- `MarkdownSectionTreeBuilder`: parses Markdown content with `flexmark-java` and builds a heading tree with section boundaries.
- `MarkdownSectionNode`: internal representation of one section, including heading text, heading level, local body text, and child sections.
- `PlainTextChunker` or equivalent private helper inside `KnowledgeChunkSplitter`: performs sliding-window fallback for non-Markdown content and oversized leaf sections.

The exact helper class names can still be adjusted during implementation, but the responsibilities should stay separated.

## Parsing And Chunking Flow

1. `KnowledgeChunkSplitter.split(...)` determines whether the uploaded file is Markdown by filename or document type metadata.
2. For Markdown:
   - Parse the document with `flexmark-java`.
   - Build a section tree from heading nodes.
   - Walk each top-level section in document order.
   - For each section:
     - Compose the candidate chunk text using the section heading line plus that section's direct body content.
     - If the candidate fits inside `chunk-size`, emit one chunk with the full heading path.
     - If the candidate is too large and child sections exist, recurse into child sections instead of emitting the parent.
     - If the section is a leaf and still too large, apply sliding-window chunking to the full leaf-section text.
3. For Markdown documents without headings:
   - Skip tree recursion and go directly to sliding-window chunking over the full content.
4. For non-Markdown:
   - Keep the existing non-Markdown behavior, but replace truncation with sliding-window chunking if necessary so no content is lost.

## Heading And Chunk Metadata

- Markdown chunk `heading` stores the full section path, joined with ` > `.
- A top-level chunk keeps only the top-level heading text.
- Leaf-section sliding-window chunks inherit the same heading path and are differentiated by `chunkIndex`.
- Headingless Markdown documents keep `heading = null`.
- Existing metadata such as `category` and `documentType` remains unchanged and should continue to flow into every emitted chunk.

## Text Assembly Rules

- Each Markdown chunk body should include the heading line for the section it represents.
- When a section is recursively split into child sections, do not emit an additional parent-body chunk.
- Sliding-window chunking over a leaf section should preserve order and overlap using `rag.chunk-overlap`.
- Chunk text must never exceed `rag.chunk-size`.

## Error Handling And Degradation

- Invalid or unparsable Markdown should degrade safely to the existing plain-text chunking path instead of failing the upload.
- Markdown documents with irregular heading nesting should still be chunked in source order based on the AST.
- If the parser yields no headings, treat the document as headingless Markdown and use sliding-window chunking.

## Testing Strategy

Add focused tests around externally visible behavior rather than AST internals:

- Markdown with multiple level-1 headings should still split into section-aligned chunks.
- An oversized level-1 section with level-2 children should recurse and emit child-based chunks.
- An oversized leaf section should fall back to sliding-window chunks that all respect `chunk-size`.
- Markdown chunks should expose a full heading path in `KnowledgeChunk.heading`.
- Headingless Markdown should still chunk without truncation or data loss.
- Non-Markdown inputs should keep their current behavior and avoid regressions.

## Non-Goals

- No new retrieval ranking logic or citation rendering changes in this iteration.
- No Markdown-specific metadata beyond the heading path.
- No attempt to preserve full Markdown formatting fidelity in chunk text beyond keeping headings and content order stable.

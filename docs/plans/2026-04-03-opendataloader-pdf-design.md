# OpenDataLoader PDF Migration Design

## Goal

Replace the current hand-written PDF parsing path based on Apache PDFBox geometry heuristics with `org.opendataloader:opendataloader-pdf-core`, while keeping the existing `KnowledgeDocumentParser -> ParsedKnowledgeDocument` contract unchanged. This iteration is limited to Java local parsing only. It does not include hybrid mode, OCR, or any future-facing integration hooks.

## Scope

- Replace the production PDF extraction implementation used by [`KnowledgeDocumentParser`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/utils/rag/KnowledgeDocumentParser.java)
- Add the Maven dependency for `opendataloader-pdf-core`
- Remove the current custom PDF text-line and layout reconstruction logic
- Update parser tests to validate the new black-box behavior

## Non-Goals

- No hybrid mode
- No OCR support
- No backward-compatibility shim for the current `pdfbox` parser
- No special interface reserved for future OCR integration
- No changes to downstream chunking, embedding, or `ParsedKnowledgeDocument`

## Decisions

- Use `opendataloader-pdf-core` as the only PDF extraction engine in production
- Treat the library output Markdown as the canonical extracted text for PDFs
- Keep `KnowledgeDocumentParser` as the single file-type dispatch entry point
- Use temporary input and output files because the Java SDK public quick-start is file-oriented rather than `byte[]`-oriented
- Normalize errors at the parser boundary so callers receive stable, controlled exceptions

## Architecture

### Entry Point

[`KnowledgeDocumentParser`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/utils/rag/KnowledgeDocumentParser.java) remains responsible only for file-type dispatch. Markdown and text files stay unchanged. The `.pdf` branch delegates to a dedicated OpenDataLoader-backed extractor.

### PDF Extraction Path

The new extractor is responsible for:

1. writing uploaded PDF bytes to a temporary `.pdf` file
2. invoking the OpenDataLoader Java SDK against that file with Markdown output enabled
3. reading the generated Markdown file from a temporary output directory
4. trimming and validating the extracted content
5. deleting all temporary artifacts in a `finally` block

The extractor returns plain text content for `ParsedKnowledgeDocument`, so downstream code remains unaware of the library swap.

### Removed Custom Logic

The current classes under [`src/main/java/com/agent/editor/utils/rag/pdf`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/utils/rag/pdf) encode project-local heuristics for:

- line merging
- double-column detection
- TOC filtering
- table formatting
- image-only OCR failure messaging

These heuristics are removed rather than preserved. The migration goal is to trust the mature library output, not to keep maintaining an internal second PDF parser.

## Behavior Changes

The following behavior changes are intentional:

- Double-column ordering is now delegated entirely to OpenDataLoader.
- TOC and noise removal are only asserted if the library naturally produces that result.
- Table output may become Markdown tables or another library-defined readable form.
- Image-only or scan-heavy PDFs fail as generic extraction failures or empty-text failures, not as `"OCR not enabled yet"`.

## Error Handling

At the parser boundary, PDF failures are collapsed into two controlled cases:

- `Failed to extract PDF content`: thrown when temporary file handling or SDK execution fails
- `PDF contains no extractable text`: thrown when the SDK succeeds but the resulting Markdown is blank

This keeps the outward contract simple and avoids misleading OCR wording in a non-OCR release.

## Testing Strategy

Keep [`KnowledgeDocumentParserTest`](/Users/eumendies/code/java/learn/agent-editor/src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java) as the primary regression suite, but adjust it to match the new library-backed contract.

### Preserve

- markdown parsing
- unsupported format rejection
- basic PDF success path

### Keep As Black-Box Validation

- double-column PDF should still read in a sensible order

### Rewrite Or Remove

- TOC noise filtering assertions tied to current dotted-line heuristics
- table-formatting assertions tied to the current custom formatter
- sentence-vs-table heuristics
- OCR-specific error-message assertions

### Failure Coverage

- image-only PDF should still fail with a controlled exception
- blank extraction result should be asserted explicitly

## Risks

- The Java SDK API may require file-path-based processing and output discovery, which introduces temp-file management complexity.
- OpenDataLoader output format may differ enough that some existing PDF assertions become invalid and need test re-baselining.
- If the library dependency cannot be resolved or its Java API differs from the public quick-start examples, the implementation may need a small adapter spike before full replacement.

## Rollout

Use TDD for the migration:

1. rewrite PDF tests around black-box parser behavior
2. watch them fail against the current implementation
3. swap the extractor to OpenDataLoader with minimal production changes
4. remove obsolete custom PDF classes once the new tests are green

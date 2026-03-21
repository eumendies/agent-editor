# PDF Knowledge Parser Design

## Goal

Extend the knowledge document upload pipeline to support PDF files, improve reading order for common double-column layouts, remove high-frequency noise such as covers and tables of contents, and preserve simple table structure in the extracted text. Keep the implementation compatible with the existing `ParsedKnowledgeDocument` contract and reserve an OCR fallback extension point without enabling OCR in this iteration.

## Decisions

- Keep `KnowledgeDocumentParser` as the single upload entry point and add PDF dispatch there.
- Add a dedicated PDF parsing pipeline instead of expanding all logic inside one class.
- Use Apache PDFBox as the only new parsing dependency in this iteration.
- Treat OCR as a pluggable fallback interface only; do not ship a real OCR integration yet.
- Preserve the current `ParsedKnowledgeDocument(content, documentType)` output so the downstream chunking and embedding flow remains unchanged.
- Prefer conservative cleanup rules: avoid deleting正文 unless strong noise signals are present.
- Prefer graceful degradation: if layout reconstruction fails, fall back to basic text extraction instead of rejecting the upload.

## Components

- `KnowledgeDocumentParser`: routes `md`, `txt`, and `pdf` files to the appropriate parsing logic
- `PdfKnowledgeExtractor`: orchestrates PDF extraction, layout reconstruction, cleanup, and formatting
- `PdfTextExtractor`: extracts page-level text blocks and geometry metadata from PDFBox
- `PdfLayoutReconstructor`: determines whether a page is single-column or double-column and rebuilds reading order
- `PdfNoiseCleaner`: removes covers, tables of contents, repeated headers/footers, page numbers, and similar noise
- `PdfStructureFormatter`: converts reconstructed blocks into stable plain text with headings, lists, and simple table rows preserved
- `PdfOcrFallback`: extension point for future OCR on near-image-only pages; default implementation is no-op in this version

## Parsing Flow

1. `KnowledgeDocumentParser` detects the `.pdf` extension and delegates to `PdfKnowledgeExtractor`.
2. `PdfTextExtractor` uses PDFBox to extract page-local text blocks with coordinates such as page index, x/y position, width, height, and raw text.
3. `PdfLayoutReconstructor` analyzes horizontal block distribution per page:
   - If two stable text bands appear with meaningful whitespace between them, the page is treated as double-column.
   - Double-column pages are emitted in `left column top-to-bottom`, then `right column top-to-bottom` order.
   - Ambiguous pages fall back to single-column ordering to reduce false positives.
4. `PdfNoiseCleaner` removes strong-noise candidates:
   - cover-like first/last pages with low body density
   - table-of-contents pages with repeated short lines and trailing page numbers
   - repeated header/footer text across pages
   - standalone page numbers, watermarks, and similar short boilerplate
5. `PdfStructureFormatter` emits cleaned text suitable for chunking:
   - preserve heading separation
   - preserve list markers
   - repair common line-break artifacts
   - render simple aligned tables as `col1 | col2 | col3`
6. If a page has near-zero extractable text, the extractor records it as an OCR candidate and continues. If the whole PDF is effectively image-only, parsing fails with a controlled “OCR not enabled” style error.

## Double-Column Heuristics

- Use per-page block x-position density rather than whole-document assumptions.
- Require consistent left and right text regions plus a visible center gap before switching to double-column mode.
- Keep the algorithm page-local so mixed-layout PDFs degrade safely.
- When the signal is weak, stay in single-column mode.

## Noise Cleanup Heuristics

- Cover detection: very short title-heavy page with low text density and little paragraph structure
- TOC detection: many short lines, dotted leaders or aligned numeric suffixes, and terms such as `目录` or `contents`
- Header/footer detection: repeated short strings at similar top/bottom coordinates across multiple pages
- Page-number removal: isolated numeric lines with strong footer/header positioning

## Table Preservation

- Do not attempt full semantic table recovery in this iteration.
- If multiple rows align against a stable set of x positions, treat the region as a simple table.
- Emit rows with ` | ` separators so downstream retrieval can preserve row and column relationships in plain text.
- If alignment is too weak, fall back to line-based text rather than producing a malformed table.

## Error Handling And Degradation

- Unsupported file types still fail with the existing unsupported-format exception.
- PDF open/read failures fail with a controlled parser exception.
- Layout reconstruction failure falls back to basic text order for the affected page or document.
- Near-image-only PDFs fail with a specific message indicating OCR is not enabled yet.

## Testing Strategy

- Expand `KnowledgeDocumentParserTest` to cover:
  - existing markdown/text behavior
  - successful PDF parsing
  - unsupported format rejection
- Add focused PDF extraction tests for:
  - double-column reading order
  - cover / TOC cleanup
  - simple table formatting
  - image-only PDF controlled failure
- Store compact PDF fixtures under `src/test/resources` when the layout matters.
- Prefer behavior-oriented assertions over implementation-detail assertions.

## Non-Goals

- No real OCR integration in this iteration
- No image, formula, chart, or figure understanding
- No precise reconstruction for complex multi-page tables
- No changes to downstream chunk metadata or `ParsedKnowledgeDocument` shape

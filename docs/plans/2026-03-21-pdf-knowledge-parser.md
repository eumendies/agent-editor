# PDF Knowledge Parser Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add PDF support to the knowledge document upload parser with improved double-column reading order, conservative noise cleanup, simple table preservation, and an OCR fallback extension point that is not enabled yet.

**Architecture:** Keep `KnowledgeDocumentParser` as the upload entry point and route PDFs into a dedicated extraction pipeline. The PDF pipeline should separate raw extraction, layout reconstruction, cleanup, and formatting so the current `ParsedKnowledgeDocument` contract remains stable while later OCR support can plug in without reshaping downstream code.

**Tech Stack:** Spring Boot 3.2, Java 17, Apache PDFBox, JUnit 5, Spring MockMultipartFile

---

## Delivery Scope

- Add `pdf` support to `KnowledgeDocumentParser`
- Introduce a PDF extraction pipeline with isolated responsibilities
- Improve reading order for common double-column PDFs
- Remove obvious cover / TOC / header / footer / page-number noise
- Preserve simple tables as pipe-delimited text rows
- Add an OCR fallback interface and default no-op behavior
- Keep `md` and `txt` behavior unchanged

## Task 1: Add parser-level PDF coverage and dependency

**Files:**
- Modify: `pom.xml`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing tests**

Add parser tests that describe the new external behavior:

```java
@Test
void shouldParsePdfDocument() {
    KnowledgeDocumentParser parser = new KnowledgeDocumentParser();
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.pdf",
            "application/pdf",
            Files.readAllBytes(Path.of("src/test/resources/knowledge/sample-basic.pdf"))
    );

    ParsedKnowledgeDocument parsed = parser.parse(file);

    assertEquals("PDF", parsed.documentType());
    assertTrue(parsed.content().contains("Knowledge Parser PDF Sample"));
}

@Test
void shouldRejectUnsupportedBinaryFormat() {
    KnowledgeDocumentParser parser = new KnowledgeDocumentParser();
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.bin",
            "application/octet-stream",
            new byte[]{1, 2, 3}
    );

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse(file));

    assertTrue(error.getMessage().contains("Unsupported knowledge document format"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL because PDF parsing is unsupported and `pdfbox` is not on the classpath.

**Step 3: Write minimal implementation**

- Add the Apache PDFBox dependency in `pom.xml`
- Extend `KnowledgeDocumentParser` just enough to recognize `.pdf` and delegate to a placeholder PDF extractor method that currently returns extracted text

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS for markdown/text regression coverage and the new basic PDF test

**Step 5: Commit**

```bash
git add pom.xml src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java
git commit -m "feat: add basic pdf knowledge parsing support"
```

### Task 2: Add PDF fixture generation and double-column regression tests

**Files:**
- Create: `src/test/resources/knowledge/sample-basic.pdf`
- Create: `src/test/resources/knowledge/sample-double-column.pdf`
- Create: `src/test/resources/knowledge/sample-cover-toc.pdf`
- Create: `src/test/resources/knowledge/sample-table.pdf`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing tests**

Add behavior-oriented tests for the PDF cases that matter:

```java
@Test
void shouldReadDoubleColumnPdfInColumnOrder() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-double-column.pdf"));

    assertTrue(parsed.content().indexOf("Left Column A") < parsed.content().indexOf("Left Column B"));
    assertTrue(parsed.content().indexOf("Left Column B") < parsed.content().indexOf("Right Column A"));
}

@Test
void shouldRemoveCoverAndTableOfContentsNoise() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-cover-toc.pdf"));

    assertFalse(parsed.content().contains("目录"));
    assertFalse(parsed.content().contains("........"));
    assertTrue(parsed.content().contains("Chapter One"));
}

@Test
void shouldPreserveSimpleTableRows() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-table.pdf"));

    assertTrue(parsed.content().contains("Name | Score | Rank"));
    assertTrue(parsed.content().contains("Alice | 95 | 1"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL because layout reconstruction, cleanup, and table formatting are not implemented yet.

**Step 3: Write minimal implementation**

- Add stable PDF fixtures under `src/test/resources/knowledge`
- Keep tests focused on ordering and cleanup outcomes, not internal classes

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL still, but now due to missing production logic instead of missing fixtures

**Step 5: Commit**

```bash
git add src/test/resources/knowledge src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "test: add pdf parser behavior fixtures"
```

### Task 3: Introduce raw PDF extraction and OCR fallback interfaces

**Files:**
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java`
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java`
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfOcrFallback.java`
- Create: `src/main/java/com/agent/editor/utils/pdf/NoOpPdfOcrFallback.java`
- Create: `src/main/java/com/agent/editor/utils/pdf/model/PdfTextBlock.java`
- Modify: `src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Add a test for image-only PDFs:

```java
@Test
void shouldFailGracefullyWhenPdfNeedsOcr() {
    IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(pdfFixture("sample-image-only.pdf"))
    );

    assertTrue(error.getMessage().contains("OCR"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL because the parser has no OCR fallback contract or controlled failure path.

**Step 3: Write minimal implementation**

- Add `PdfTextBlock` as the page/block geometry carrier
- Add `PdfTextExtractor` to extract positioned text blocks from PDFBox
- Add `PdfOcrFallback` and `NoOpPdfOcrFallback`
- Add `PdfKnowledgeExtractor` orchestration with image-only detection and controlled OCR-not-enabled failure
- Wire `KnowledgeDocumentParser` to delegate `.pdf` files through `PdfKnowledgeExtractor`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS for the OCR fallback failure case while earlier PDF behavior tests still fail

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java src/main/java/com/agent/editor/utils/pdf src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: add pdf extraction pipeline skeleton"
```

### Task 4: Implement layout reconstruction for double-column pages

**Files:**
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfLayoutReconstructor.java`
- Modify: `src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java`
- Modify: `src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Focus the test on mixed reading order:

```java
@Test
void shouldPreferLeftThenRightReadingOrderForDoubleColumnPages() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-double-column.pdf"));

    String content = parsed.content();
    assertTrue(content.indexOf("Left Column Intro") < content.indexOf("Right Column Intro"));
    assertTrue(content.indexOf("Left Column Conclusion") < content.indexOf("Right Column Intro"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldPreferLeftThenRightReadingOrderForDoubleColumnPages test`

Expected: FAIL because basic extraction emits PDFBox default order instead of reconstructed column order.

**Step 3: Write minimal implementation**

- Group blocks per page
- Estimate page center gap and left/right text bands
- Reorder blocks per detected column before formatting
- Fall back to single-column sorting when confidence is low

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldPreferLeftThenRightReadingOrderForDoubleColumnPages test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/pdf/PdfLayoutReconstructor.java src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java src/main/java/com/agent/editor/utils/pdf/PdfTextExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: improve pdf reading order for double-column layouts"
```

### Task 5: Implement conservative cover / TOC / header-footer cleanup

**Files:**
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfNoiseCleaner.java`
- Modify: `src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldDropRepeatedHeadersAndTocNoise() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-cover-toc.pdf"));

    String content = parsed.content();
    assertFalse(content.contains("目录"));
    assertFalse(content.contains("1 ........ 3"));
    assertFalse(content.contains("Company Confidential"));
    assertTrue(content.contains("Real body paragraph"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldDropRepeatedHeadersAndTocNoise test`

Expected: FAIL because the parser still includes TOC and repeated header/footer text.

**Step 3: Write minimal implementation**

- Detect repeated short header/footer candidates across pages
- Detect TOC-like pages using dotted leaders, short title density, and trailing page numbers
- Detect cover-like low-density title pages
- Remove only high-confidence noise

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldDropRepeatedHeadersAndTocNoise test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/pdf/PdfNoiseCleaner.java src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: clean noisy sections from pdf knowledge documents"
```

### Task 6: Preserve simple table structure in formatted output

**Files:**
- Create: `src/main/java/com/agent/editor/utils/pdf/PdfStructureFormatter.java`
- Modify: `src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldFormatAlignedTextAsSimpleTableRows() {
    ParsedKnowledgeDocument parsed = parser.parse(pdfFixture("sample-table.pdf"));

    String content = parsed.content();
    assertTrue(content.contains("Quarter | Revenue | Growth"));
    assertTrue(content.contains("Q1 | 120 | 12%"));
    assertTrue(content.contains("Q2 | 145 | 21%"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldFormatAlignedTextAsSimpleTableRows test`

Expected: FAIL because aligned cell text is still emitted as loose line fragments.

**Step 3: Write minimal implementation**

- Detect stable x-position alignments within nearby rows
- Convert detected table rows to ` | ` delimited output
- Keep headings, lists, and normal paragraphs readable in the same formatter

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldFormatAlignedTextAsSimpleTableRows test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/pdf/PdfStructureFormatter.java src/main/java/com/agent/editor/utils/pdf/PdfKnowledgeExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: preserve simple table structure in pdf parsing"
```

### Task 7: Run full parser verification and regression checks

**Files:**
- Modify: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`
- Verify: `src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java`
- Verify: `src/main/java/com/agent/editor/utils/pdf/*.java`

**Step 1: Write the failing test**

Add one regression-style test to prove markdown and text still work after the PDF refactor:

```java
@Test
void shouldKeepParsingMarkdownAndTextAfterPdfSupport() {
    ParsedKnowledgeDocument markdown = parser.parse(new MockMultipartFile(
            "file", "notes.md", "text/markdown", "# Title\nBody".getBytes(StandardCharsets.UTF_8)
    ));
    ParsedKnowledgeDocument text = parser.parse(new MockMultipartFile(
            "file", "notes.txt", "text/plain", "plain body".getBytes(StandardCharsets.UTF_8)
    ));

    assertEquals("MARKDOWN", markdown.documentType());
    assertEquals("TEXT", text.documentType());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL if the refactor regressed non-PDF handling.

**Step 3: Write minimal implementation**

- Fix any parser routing regressions
- Remove duplicated formatting code
- Keep exception messages stable where possible

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS

Then run the broader upload-path regression:

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeBaseServiceTest,KnowledgeBaseServiceMilvusTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/KnowledgeDocumentParser.java src/main/java/com/agent/editor/utils/pdf src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "test: verify pdf parser integration regressions"
```

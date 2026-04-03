# OpenDataLoader PDF Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current custom PDFBox-based PDF parser with `opendataloader-pdf-core` and keep `KnowledgeDocumentParser` returning extracted PDF content through the existing `ParsedKnowledgeDocument` contract.

**Architecture:** The parser entry point stays unchanged and delegates `.pdf` files to a new OpenDataLoader-backed extractor. The extractor writes the uploaded bytes to a temporary PDF, runs the Java SDK to generate Markdown into a temporary output directory, reads the generated Markdown back into memory, and cleans up temporary files. Old PDF geometry and heuristic classes are removed instead of being preserved as fallback behavior.

**Tech Stack:** Spring Boot 3.2, Java 17, Maven, OpenDataLoader PDF Core, Apache PDFBox for test fixture generation, JUnit 5, Spring MockMultipartFile

---

### Task 1: Rewrite parser tests around the new PDF contract

**Files:**
- Modify: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Adjust the existing PDF tests so they assert black-box parser outcomes instead of the current custom heuristic details:

```java
@Test
void shouldParsePdfDocument() throws IOException {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "knowledge.pdf",
            "application/pdf",
            createPdf("Knowledge Parser PDF Sample", 72, 720)
    );

    ParsedKnowledgeDocument parsed = new KnowledgeDocumentParser().parse(file);

    assertEquals("PDF", parsed.getDocumentType());
    assertTrue(parsed.getContent().contains("Knowledge Parser PDF Sample"));
}

@Test
void shouldReadDoubleColumnPdfInSensibleOrder() throws IOException {
    ParsedKnowledgeDocument parsed = new KnowledgeDocumentParser().parse(pdfFile("double-column.pdf", createDoubleColumnPdf()));

    String content = parsed.getContent();
    assertTrue(content.indexOf("Left Column A") < content.indexOf("Right Column A"), content);
}

@Test
void shouldFailGracefullyWhenPdfContainsNoExtractableText() throws IOException {
    IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new KnowledgeDocumentParser().parse(pdfFile("scan.pdf", createImageOnlyPdf()))
    );

    assertFalse(error.getMessage().isBlank());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL because the current implementation either returns old custom behavior or throws the old OCR-specific error contract.

**Step 3: Write minimal implementation**

Do not change production code yet. Only finish rewriting the tests so they represent the desired post-migration behavior.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL, but now for the right reason: production still uses the old PDF parser implementation.

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "test: realign pdf parser expectations for opendataloader"
```

### Task 2: Add the OpenDataLoader dependency and a minimal extractor shell

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/agent/editor/utils/rag/KnowledgeDocumentParser.java`
- Create: `src/main/java/com/agent/editor/utils/rag/pdf/OpenDataLoaderPdfKnowledgeExtractor.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Add or refine one test that requires the parser to return extracted PDF content through a dedicated extractor class rather than the current `PdfKnowledgeExtractor` implementation details.

```java
@Test
void shouldParsePdfThroughOpenDataLoaderExtractor() throws IOException {
    ParsedKnowledgeDocument parsed = new KnowledgeDocumentParser().parse(
            pdfFile("knowledge.pdf", createPdf("OpenDataLoader Sample", 72, 720))
    );

    assertTrue(parsed.getContent().contains("OpenDataLoader Sample"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldParsePdfThroughOpenDataLoaderExtractor test`

Expected: FAIL because the dependency and extractor class do not exist yet.

**Step 3: Write minimal implementation**

- Add `org.opendataloader:opendataloader-pdf-core` to `pom.xml`
- Introduce `OpenDataLoaderPdfKnowledgeExtractor`
- Change `KnowledgeDocumentParser` so the `.pdf` branch delegates to the new extractor
- Keep the extractor body skeletal if needed, but compile it through the real SDK entry points

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest#shouldParsePdfThroughOpenDataLoaderExtractor test`

Expected: PASS for the focused extraction case

**Step 5: Commit**

```bash
git add pom.xml src/main/java/com/agent/editor/utils/rag/KnowledgeDocumentParser.java src/main/java/com/agent/editor/utils/rag/pdf/OpenDataLoaderPdfKnowledgeExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: add opendataloader pdf extractor shell"
```

### Task 3: Implement temp-file based PDF extraction and controlled failures

**Files:**
- Modify: `src/main/java/com/agent/editor/utils/rag/pdf/OpenDataLoaderPdfKnowledgeExtractor.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Pin the two controlled failure outcomes:

```java
@Test
void shouldThrowControlledExceptionWhenPdfExtractionFails() {
    OpenDataLoaderPdfKnowledgeExtractor extractor = new OpenDataLoaderPdfKnowledgeExtractor(/* test seam */);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> extractor.extract(new byte[]{1, 2, 3}));

    assertTrue(error.getMessage().contains("Failed to extract PDF content"));
}

@Test
void shouldThrowControlledExceptionWhenPdfOutputIsBlank() throws IOException {
    IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new KnowledgeDocumentParser().parse(pdfFile("scan.pdf", createImageOnlyPdf()))
    );

    assertTrue(error.getMessage().contains("extractable text"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL because temp-file orchestration and controlled blank-output handling are not finished.

**Step 3: Write minimal implementation**

- Write PDF bytes to a temp input file
- Create a temp output directory
- Invoke the OpenDataLoader Java API to generate Markdown
- Read the generated Markdown file
- Throw `IllegalArgumentException("PDF contains no extractable text")` when the result is blank
- Wrap IO or SDK failures in `IllegalArgumentException("Failed to extract PDF content", cause)`
- Clean up temp files in `finally`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS for the basic success and controlled failure cases

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/rag/pdf/OpenDataLoaderPdfKnowledgeExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: implement opendataloader temp-file extraction"
```

### Task 4: Remove obsolete custom PDF parser code

**Files:**
- Delete: `src/main/java/com/agent/editor/utils/rag/pdf/PdfKnowledgeExtractor.java`
- Delete: `src/main/java/com/agent/editor/utils/rag/pdf/PdfTextExtractor.java`
- Delete: `src/main/java/com/agent/editor/utils/rag/pdf/PdfTextLine.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

Add or keep one regression test that proves the parser still handles non-PDF formats while the PDF code path no longer depends on the deleted classes:

```java
@Test
void shouldParseMarkdownWithoutPdfLegacyClasses() {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "resume.md",
            "text/markdown",
            "# Title".getBytes(StandardCharsets.UTF_8)
    );

    ParsedKnowledgeDocument parsed = new KnowledgeDocumentParser().parse(file);

    assertEquals("MARKDOWN", parsed.getDocumentType());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: FAIL if any production code still references the old PDF classes.

**Step 3: Write minimal implementation**

- Delete the old custom PDF parser classes
- Remove any remaining imports or constructors that still instantiate them
- Keep the parser wiring simple and single-path

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/rag/KnowledgeDocumentParser.java src/main/java/com/agent/editor/utils/rag/pdf/OpenDataLoaderPdfKnowledgeExtractor.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git rm src/main/java/com/agent/editor/utils/rag/pdf/PdfKnowledgeExtractor.java src/main/java/com/agent/editor/utils/rag/pdf/PdfTextExtractor.java src/main/java/com/agent/editor/utils/rag/pdf/PdfTextLine.java
git commit -m "refactor: remove legacy pdfbox parser heuristics"
```

### Task 5: Run focused verification and full regression

**Files:**
- Modify: none
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing test**

No new test. Use the full regression suite assembled in earlier tasks.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeDocumentParserTest test`

Expected: PASS before this task starts. If it fails, fix the regression before continuing.

**Step 3: Write minimal implementation**

No implementation change. This task exists to verify that focused parser coverage is stable before broader regression.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS for the full Maven test suite

**Step 5: Commit**

```bash
git add .
git commit -m "test: verify opendataloader pdf migration"
```

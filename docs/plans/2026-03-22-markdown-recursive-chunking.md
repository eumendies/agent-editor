# Markdown Recursive Chunking Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add AST-based recursive Markdown chunking so knowledge-base ingestion splits by section hierarchy first and only falls back to sliding-window chunking when the smallest section still exceeds the configured size limit.

**Architecture:** Keep `KnowledgeChunkSplitter` as the public chunking entry point, but route Markdown files through a `flexmark-java` section-tree builder. The splitter should recurse through heading sections, emit heading-path-aware chunks, and reuse a shared sliding-window fallback for oversized leaf sections and headingless content.

**Tech Stack:** Spring Boot 3.2, Java 17, flexmark-java, JUnit 5, Spring MockMultipartFile

---

## Delivery Scope

- Add a Markdown parser dependency for AST traversal
- Replace regex-only Markdown chunking with section-tree recursion
- Preserve full heading paths in Markdown chunk metadata
- Replace truncation with sliding-window fallback for oversized leaf sections
- Keep non-Markdown chunking behavior compatible

### Task 1: Add dependency and behavior-first chunking tests

**Files:**
- Modify: `pom.xml`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java`

**Step 1: Write the failing tests**

Expand `KnowledgeChunkSplitterTest` with behavior-focused cases:

```java
@Test
void shouldRecursivelySplitMarkdownByNestedHeadings() {
    RagProperties properties = new RagProperties(80, 10, 5, 8, 12);
    KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
    String content = """
            # 项目经历
            简介简介简介简介简介简介简介简介简介简介

            ## Agent Editor
            Agent Editor 项目内容 Agent Editor 项目内容 Agent Editor 项目内容

            ## 智能检索
            智能检索项目内容 智能检索项目内容 智能检索项目内容
            """;

    List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

    assertEquals(2, chunks.size());
    assertEquals("项目经历 > Agent Editor", chunks.get(0).heading());
    assertEquals("项目经历 > 智能检索", chunks.get(1).heading());
}

@Test
void shouldFallbackToSlidingWindowWhenLeafSectionExceedsChunkSize() {
    RagProperties properties = new RagProperties(60, 10, 5, 8, 12);
    KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
    String content = """
            # 项目经历
            ## Agent Editor
            这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容，
            这里是一段特别长的叶子章节内容，这里是一段特别长的叶子章节内容。
            """;

    List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

    assertTrue(chunks.size() > 1);
    assertTrue(chunks.stream().allMatch(chunk -> chunk.heading().equals("项目经历 > Agent Editor")));
    assertTrue(chunks.stream().allMatch(chunk -> chunk.chunkText().length() <= 60));
}

@Test
void shouldKeepHeadingNullForHeadinglessMarkdown() {
    RagProperties properties = new RagProperties(40, 10, 5, 8, 12);
    KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);

    List<KnowledgeChunk> chunks = splitter.split("doc-1", "notes.md", "纯正文内容纯正文内容纯正文内容纯正文内容", Map.of());

    assertFalse(chunks.isEmpty());
    assertNull(chunks.get(0).heading());
}
```

Add a regression assertion for non-Markdown input if needed in the same test class.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: FAIL because the current splitter only recognizes level-1 headings and truncates oversized content.

**Step 3: Write minimal implementation**

- Add `flexmark-java` to `pom.xml`
- Do not change production logic beyond what is required to compile against the new tests yet

**Step 4: Run test to verify it still fails for the right reason**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: FAIL on recursive chunking assertions, not due to missing dependencies or compilation errors

**Step 5: Commit**

```bash
git add pom.xml src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java
git commit -m "test: define recursive markdown chunking behavior"
```

### Task 2: Build the Markdown section tree

**Files:**
- Create: `src/main/java/com/agent/editor/utils/markdown/MarkdownSectionNode.java`
- Create: `src/main/java/com/agent/editor/utils/markdown/MarkdownSectionTreeBuilder.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java`

**Step 1: Write the failing test**

Add a focused test that requires full heading-path reconstruction through nested sections:

```java
@Test
void shouldBuildFullHeadingPathForNestedMarkdownSections() {
    RagProperties properties = new RagProperties(500, 80, 5, 8, 12);
    KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
    String content = """
            # 项目经历
            ## Agent Editor
            ### 检索增强
            负责 Markdown 递归分块
            """;

    List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of());

    assertEquals(1, chunks.size());
    assertEquals("项目经历 > Agent Editor > 检索增强", chunks.get(0).heading());
    assertTrue(chunks.get(0).chunkText().startsWith("### 检索增强"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: FAIL because there is no AST-backed section-tree builder and no heading-path support.

**Step 3: Write minimal implementation**

Implement the smallest viable Markdown tree support:

```java
public record MarkdownSectionNode(
        String headingText,
        int headingLevel,
        String headingLine,
        String bodyText,
        List<MarkdownSectionNode> children
) {
}
```

```java
public class MarkdownSectionTreeBuilder {

    public List<MarkdownSectionNode> buildSections(String markdown) {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);
        // Walk heading nodes in source order, assemble parent-child relationships,
        // and capture each section's direct body text.
    }
}
```

Keep the builder package-private or internal to `utils.markdown`; do not expose it beyond chunking.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: PASS for heading-path reconstruction while the oversized-leaf fallback test may still fail

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/markdown src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java
git commit -m "feat: add markdown section tree builder"
```

### Task 3: Refactor the splitter to recurse through section hierarchy

**Files:**
- Modify: `src/main/java/com/agent/editor/utils/KnowledgeChunkSplitter.java`
- Modify: `src/main/java/com/agent/editor/utils/markdown/MarkdownSectionTreeBuilder.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java`

**Step 1: Write the failing test**

Add a regression test to prove oversized parent sections do not emit duplicate parent chunks:

```java
@Test
void shouldPreferChildSectionsOverDuplicatingOversizedParentSection() {
    RagProperties properties = new RagProperties(90, 10, 5, 8, 12);
    KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
    String content = """
            # 项目经历
            父章节前言父章节前言父章节前言父章节前言父章节前言父章节前言

            ## Agent Editor
            子章节内容子章节内容子章节内容
            """;

    List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of());

    assertEquals(1, chunks.size());
    assertEquals("项目经历 > Agent Editor", chunks.get(0).heading());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: FAIL because the splitter still cannot recurse through child sections and sliding-window fallback is incomplete.

**Step 3: Write minimal implementation**

Refactor `KnowledgeChunkSplitter` so it:

- Detects Markdown files by `.md` or `.markdown`
- Uses `MarkdownSectionTreeBuilder` to obtain top-level sections
- Recursively emits section chunks only when the current section fits
- Descends into children when the current section exceeds `chunk-size`
- Falls back to sliding-window chunking for headingless Markdown and oversized leaf sections
- Preserves metadata and assigns stable `chunkIndex` values in output order

Prefer small private helpers such as:

```java
private void appendMarkdownChunks(List<KnowledgeChunk> result, String documentId, String fileName, MarkdownSectionNode section, List<String> headingPath, Map<String, String> metadata)
```

and

```java
private List<String> slidingWindows(String text)
```

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/utils/KnowledgeChunkSplitter.java src/main/java/com/agent/editor/utils/markdown/MarkdownSectionTreeBuilder.java src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java
git commit -m "feat: add recursive markdown knowledge chunking"
```

### Task 4: Verify integration behavior for knowledge upload

**Files:**
- Modify: `src/test/java/com/agent/editor/service/KnowledgeBaseServiceMilvusTest.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeBaseServiceTest.java`

**Step 1: Write the failing test**

Add an integration-level assertion that Markdown upload persists recursive chunks with heading paths:

```java
@Test
void shouldPersistMarkdownChunksWithHeadingPath() {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "resume.md",
            "text/markdown",
            """
            # 项目经历
            ## Agent Editor
            负责 Markdown 递归分块
            """.getBytes(StandardCharsets.UTF_8)
    );

    when(parser.parse(file)).thenReturn(new ParsedKnowledgeDocument("""
            # 项目经历
            ## Agent Editor
            负责 Markdown 递归分块
            """, "markdown"));

    service.upload(file, "resume");

    verify(repository).saveAll(chunksCaptor.capture());
    assertEquals("项目经历 > Agent Editor", chunksCaptor.getValue().get(0).heading());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeBaseServiceMilvusTest,KnowledgeBaseServiceTest test`

Expected: FAIL because integration tests do not yet cover the heading-path behavior.

**Step 3: Write minimal implementation**

Only adjust production code if the new integration assertion reveals a real flow gap. Otherwise keep this step test-only.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeBaseServiceMilvusTest,KnowledgeBaseServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/service/KnowledgeBaseServiceMilvusTest.java src/test/java/com/agent/editor/service/KnowledgeBaseServiceTest.java
git commit -m "test: cover recursive markdown chunk persistence"
```

### Task 5: Run focused verification and full regression checks

**Files:**
- No production file changes expected

**Step 1: Run focused splitter and service tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeChunkSplitterTest,KnowledgeBaseServiceMilvusTest,KnowledgeBaseServiceTest test`

Expected: PASS

**Step 2: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

**Step 3: Commit final verification state**

```bash
git add pom.xml src/main/java/com/agent/editor/utils/KnowledgeChunkSplitter.java src/main/java/com/agent/editor/utils/markdown src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java src/test/java/com/agent/editor/service/KnowledgeBaseServiceMilvusTest.java src/test/java/com/agent/editor/service/KnowledgeBaseServiceTest.java docs/plans/2026-03-22-markdown-recursive-chunking-design.md docs/plans/2026-03-22-markdown-recursive-chunking.md
git commit -m "feat: support recursive markdown knowledge chunking"
```

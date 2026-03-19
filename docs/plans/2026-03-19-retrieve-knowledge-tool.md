# Retrieve Knowledge Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a shared retrieval service and a `retrieveKnowledge` tool that lets agent.v2 query the personal knowledge base as structured chunk results.

**Architecture:** Introduce a retrieval-facing abstraction over the in-memory knowledge store, implement a minimal query scorer in `KnowledgeRetrievalService`, and wrap that service in a `RetrieveKnowledgeTool`. Keep the retrieval contract query-based so later `/rag/ask` and `/rag/write` can reuse it directly.

**Tech Stack:** Spring Boot 3.2, Java 17, LangChain4j tool specifications, JUnit 5, Mockito, Jackson

---

### Task 1: Add shared retrieval service

**Files:**
- Create: `src/main/java/com/agent/editor/service/KnowledgeChunkRepository.java`
- Create: `src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java`
- Create: `src/main/java/com/agent/editor/model/RetrievedKnowledgeChunk.java`
- Modify: `src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java`

**Step 1: Write the failing test**

```java
class KnowledgeRetrievalServiceTest {

    @Test
    void shouldReturnChunksOrderedByLexicalScore() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.saveChunk(new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of()));
        store.saveChunk(new KnowledgeChunk("doc-1", 1, "resume.md", "其他", "Redis 缓存经验", Map.of()));

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(store, new RagProperties(500, 80, 5, 8, 12));
        List<RetrievedKnowledgeChunk> chunks = service.retrieve("Spring Boot", null, null);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).chunkText().contains("Spring Boot"));
        assertTrue(chunks.get(0).score() > 0);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeRetrievalServiceTest test`
Expected: FAIL because retrieval service types do not exist.

**Step 3: Write minimal implementation**

```java
public List<RetrievedKnowledgeChunk> retrieve(String query, List<String> documentIds, Integer topK) {
    int limit = topK == null ? properties.askTopK() : topK;
    return repository.findByDocumentIds(documentIds).stream()
            .map(chunk -> new RetrievedKnowledgeChunk(
                    chunk.documentId(),
                    chunk.fileName(),
                    chunk.chunkIndex(),
                    chunk.heading(),
                    chunk.chunkText(),
                    lexicalScore(query, chunk.chunkText())
            ))
            .filter(chunk -> chunk.score() > 0)
            .sorted(Comparator.comparingDouble(RetrievedKnowledgeChunk::score).reversed())
            .limit(limit)
            .toList();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=KnowledgeRetrievalServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/KnowledgeChunkRepository.java src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java src/main/java/com/agent/editor/model/RetrievedKnowledgeChunk.java src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java
git commit -m "feat: add shared knowledge retrieval service"
```

### Task 2: Add retrieveKnowledge tool and register it

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java`

**Step 1: Write the failing test**

```java
class RetrieveKnowledgeToolTest {

    @Test
    void shouldReturnRetrievedChunksAsJson() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieve("Spring", null, null))
                .thenReturn(List.of(new RetrievedKnowledgeChunk("doc-1", "resume.md", 0, "项目经历", "Spring Boot 项目经验", 2.0)));
        RetrieveKnowledgeTool tool = new RetrieveKnowledgeTool(retrievalService);

        ToolResult result = tool.execute(
                new ToolInvocation("retrieveKnowledge", "{\"query\":\"Spring\"}"),
                new ToolContext("task-1", null)
        );

        assertTrue(result.message().contains("\"fileName\":\"resume.md\""));
        assertTrue(result.message().contains("\"score\":2.0"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RetrieveKnowledgeToolTest test`
Expected: FAIL because the tool does not exist.

**Step 3: Write minimal implementation**

```java
@Override
public ToolResult execute(ToolInvocation invocation, ToolContext context) {
    RetrieveKnowledgeArguments arguments = ToolArgumentDecoder.decode(invocation.arguments(), RetrieveKnowledgeArguments.class, name());
    return new ToolResult(objectMapper.writeValueAsString(
            retrievalService.retrieve(arguments.query(), arguments.documentIds(), arguments.topK())
    ));
}
```

Register the tool in `ToolConfig`.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RetrieveKnowledgeToolTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeArguments.java src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java src/main/java/com/agent/editor/config/ToolConfig.java src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java
git commit -m "feat: add retrieve knowledge tool"
```

# Hybrid RAG Retrieval Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace vector-only RAG retrieval with native Milvus hybrid retrieval over dense embeddings plus BM25 lexical matching on `heading + chunkText`.

**Architecture:** Keep `KnowledgeRetrievalService.retrieve(query, documentIds, topK)` as the public entry point, but move repository retrieval to a hybrid-first contract. Rebuild the Milvus collection as a hybrid-ready schema with a dedicated `fullText` field, BM25 function, dense index, and sparse index, then let `MilvusKnowledgeChunkRepository` issue `hybridSearch` with vector fallback on query failure.

**Tech Stack:** Spring Boot 3.2, Java 17, milvus-sdk-java 2.6.13, LangChain4j embeddings, JUnit 5, Mockito, Spring Boot Test

---

### Task 1: Lock down the hybrid Milvus schema with failing config tests

**Files:**
- Modify: `src/test/java/com/agent/editor/config/MilvusConfigTest.java`
- Modify: `src/test/java/com/agent/editor/config/RagPropertiesTest.java`
- Modify: `src/main/resources/application.yml`

**Step 1: Write the failing tests**

Add config assertions to `MilvusConfigTest` that inspect the `CreateCollectionReq` and `CreateIndexReq` arguments:

```java
@Test
void shouldCreateHybridCollectionSchemaWhenMissing() throws Exception {
    MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
    when(milvusClient.hasCollection(any())).thenReturn(false);
    MilvusConfig milvusConfig = new MilvusConfig();

    InitializingBean initializer = milvusConfig.milvusCollectionInitializer(
            milvusClient,
            new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 1024)
    );

    initializer.afterPropertiesSet();

    ArgumentCaptor<CreateCollectionReq> createCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
    verify(milvusClient).createCollection(createCaptor.capture());

    assertThat(createCaptor.getValue().getCollectionName()).isEqualTo("knowledge_chunks_v2");
    assertThat(createCaptor.getValue().getCollectionSchema().getFields())
            .extracting(AddFieldReq::getFieldName)
            .contains("fullText");
    assertThat(createCaptor.getValue().getCollectionSchema().getFunctions()).isNotEmpty();
}
```

Add index assertions:

```java
ArgumentCaptor<CreateIndexReq> indexCaptor = ArgumentCaptor.forClass(CreateIndexReq.class);
verify(milvusClient).createIndex(indexCaptor.capture());
assertThat(indexCaptor.getValue().getIndexParams())
        .extracting(IndexParam::getFieldName, IndexParam::getIndexType)
        .contains(
                tuple("embedding", IndexParam.IndexType.AUTOINDEX),
                tuple("sparseFullText", IndexParam.IndexType.SPARSE_INVERTED_INDEX)
        );
```

Update `RagPropertiesTest` only if you decide to introduce a retrieval candidate multiplier. Otherwise keep retrieval config unchanged and skip extra property coverage.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusConfigTest test`

Expected: FAIL because `MilvusConfig` currently creates only the dense schema and a single dense index.

**Step 3: Write minimal implementation**

Update `src/main/resources/application.yml` to switch the default collection name:

```yaml
milvus:
  collection-name: knowledge_chunks_v2
```

Do not add new retrieval tuning properties in this increment unless a test requires them.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusConfigTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/config/MilvusConfigTest.java src/main/resources/application.yml
git commit -m "test: lock hybrid milvus schema requirements"
```

### Task 2: Implement hybrid-ready Milvus collection creation and persistence payloads

**Files:**
- Modify: `src/main/java/com/agent/editor/config/MilvusConfig.java`
- Modify: `src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java`
- Modify: `src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java`

**Step 1: Write the failing test**

Extend `MilvusKnowledgeChunkRepositoryTest` to assert that `saveAll()` writes the derived lexical field:

```java
@Test
void shouldPersistHybridSearchTextAlongsideEmbedding() {
    MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
    MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
            milvusClient,
            new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
    );

    repository.saveAll(List.of(new KnowledgeChunk(
            "doc-1",
            0,
            "resume.md",
            "项目经历 > Agent Editor",
            "负责混合检索改造",
            Map.of("category", "resume", "documentType", "markdown"),
            new float[]{0.1f, 0.2f, 0.3f}
    )));

    ArgumentCaptor<UpsertReq> requestCaptor = ArgumentCaptor.forClass(UpsertReq.class);
    verify(milvusClient).upsert(requestCaptor.capture());
    assertEquals("项目经历 > Agent Editor\n负责混合检索改造",
            requestCaptor.getValue().getData().get(0).get("fullText").getAsString());
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest test`

Expected: FAIL because the upsert payload does not yet contain `fullText`.

**Step 3: Write minimal implementation**

In `MilvusConfig`:

```java
private static final String FULL_TEXT_FIELD = "fullText";
private static final String SPARSE_FULL_TEXT_FIELD = "sparseFullText";
private static final String BM25_FUNCTION_NAME = "fulltext_bm25";
```

Add the lexical text field:

```java
schema.addField(AddFieldReq.builder()
        .fieldName(FULL_TEXT_FIELD)
        .dataType(DataType.VarChar)
        .maxLength(12288)
        .enableAnalyzer(true)
        .enableMatch(true)
        .build());
```

Register the BM25 function on collection creation:

```java
schema.addFunction(CreateCollectionReq.Function.builder()
        .name(BM25_FUNCTION_NAME)
        .functionType(FunctionType.BM25)
        .inputFieldNames(List.of(FULL_TEXT_FIELD))
        .outputFieldNames(List.of(SPARSE_FULL_TEXT_FIELD))
        .build());
```

Create the sparse field and sparse index:

```java
schema.addField(AddFieldReq.builder()
        .fieldName(SPARSE_FULL_TEXT_FIELD)
        .dataType(DataType.SparseFloatVector)
        .build());
```

```java
IndexParam.builder()
        .fieldName(SPARSE_FULL_TEXT_FIELD)
        .indexName("sparse_full_text_idx")
        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
        .metricType(IndexParam.MetricType.BM25)
        .extraParams(Map.of())
        .build()
```

In `MilvusKnowledgeChunkRepository.toRow()` derive `fullText` from `heading` and `chunkText` before writing the row.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusConfigTest,MilvusKnowledgeChunkRepositoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/MilvusConfig.java src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java src/test/java/com/agent/editor/config/MilvusConfigTest.java
git commit -m "feat: add hybrid milvus collection schema"
```

### Task 3: Promote hybrid retrieval to the repository contract

**Files:**
- Modify: `src/main/java/com/agent/editor/repository/KnowledgeChunkRepository.java`
- Modify: `src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java`
- Modify: `src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java`
- Modify: `src/main/java/com/agent/editor/repository/InMemoryKnowledgeStore.java`

**Step 1: Write the failing tests**

Add a repository retrieval test that expects `hybridSearch()` rather than `search()`:

```java
@Test
void shouldUseMilvusHybridSearchForRetrieval() {
    MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
    when(milvusClient.hybridSearch(any(HybridSearchReq.class))).thenReturn(SearchResp.builder()
            .searchResults(List.of(List.of(SearchResp.SearchResult.builder()
                    .entity(Map.of(
                            "documentId", "doc-1",
                            "fileName", "resume.md",
                            "chunkIndex", 0L,
                            "heading", "项目经历",
                            "chunkText", "Spring Boot 项目经验"
                    ))
                    .score(0.93f)
                    .build())))
            .build());

    MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
            milvusClient,
            new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
    );

    List<RetrievedKnowledgeChunk> results = repository.searchHybrid(
            "spring boot",
            new float[]{0.1f, 0.2f, 0.3f},
            List.of("doc-1"),
            3
    );

    assertEquals(1, results.size());
    verify(milvusClient).hybridSearch(any(HybridSearchReq.class));
}
```

Add a fallback test:

```java
@Test
void shouldFallbackToVectorSearchWhenHybridSearchFails() {
    MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
    when(milvusClient.hybridSearch(any(HybridSearchReq.class))).thenThrow(new RuntimeException("hybrid failed"));
    when(milvusClient.search(any(SearchReq.class))).thenReturn(SearchResp.builder()
            .searchResults(List.of(List.of()))
            .build());

    MilvusKnowledgeChunkRepository repository = new MilvusKnowledgeChunkRepository(
            milvusClient,
            new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 3)
    );

    repository.searchHybrid("spring boot", new float[]{0.1f, 0.2f, 0.3f}, null, 3);

    verify(milvusClient).search(any(SearchReq.class));
}
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest test`

Expected: FAIL because the repository only exposes `searchByVector(...)`.

**Step 3: Write minimal implementation**

Change `KnowledgeChunkRepository` to expose hybrid retrieval:

```java
default List<RetrievedKnowledgeChunk> searchHybrid(String query,
                                                   float[] queryVector,
                                                   List<String> documentIds,
                                                   int topK) {
    throw new UnsupportedOperationException("Hybrid search is not implemented");
}
```

Keep `searchByVector(...)` as a fallback-only method.

In `MilvusKnowledgeChunkRepository`, implement:

```java
public List<RetrievedKnowledgeChunk> searchHybrid(String query,
                                                  float[] queryVector,
                                                  List<String> documentIds,
                                                  int topK) {
    int candidateTopK = Math.max(topK * 3, 20);
    try {
        SearchResp response = milvusClient.hybridSearch(HybridSearchReq.builder()
                .collectionName(properties.collectionName())
                .searchRequests(List.of(
                        AnnSearchReq.builder()
                                .vectorFieldName("embedding")
                                .metricType(IndexParam.MetricType.COSINE)
                                .topK(candidateTopK)
                                .filter(buildDocumentFilter(documentIds))
                                .vectors(List.of(new FloatVec(queryVector)))
                                .build(),
                        AnnSearchReq.builder()
                                .vectorFieldName("sparseFullText")
                                .metricType(IndexParam.MetricType.BM25)
                                .topK(candidateTopK)
                                .filter(buildDocumentFilter(documentIds))
                                .vectors(List.of(new EmbeddedText(query)))
                                .build()
                ))
                .topK(topK)
                .outFields(List.of("documentId", "fileName", "chunkIndex", "heading", "chunkText"))
                .build());
        return toRetrievedChunks(response);
    } catch (RuntimeException error) {
        return searchByVector(queryVector, documentIds, topK);
    }
}
```

Let `InMemoryKnowledgeStore` keep its existing storage behavior; it does not need hybrid retrieval support for this increment unless a test explicitly exercises it.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/repository/KnowledgeChunkRepository.java src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java src/main/java/com/agent/editor/repository/InMemoryKnowledgeStore.java src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java
git commit -m "feat: add hybrid repository retrieval"
```

### Task 4: Switch the retrieval service to the hybrid repository API

**Files:**
- Modify: `src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceVectorTest.java`

**Step 1: Write the failing tests**

Update the service tests to verify `searchHybrid(...)` is used:

```java
@Test
void shouldHonorExplicitTopKWhenRetrievingHybridResults() {
    KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
    KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
    when(embeddingService.embed("Spring Boot")).thenReturn(new float[]{0.1f, 0.2f});
    KnowledgeRetrievalService service = new KnowledgeRetrievalService(
            repository,
            embeddingService,
            new RagProperties(500, 80, 5, 8, 12)
    );

    service.retrieve("Spring Boot", List.of("doc-1"), 2);

    verify(repository).searchHybrid(eq("Spring Boot"), any(), eq(List.of("doc-1")), eq(2));
}
```

Add blank-query coverage if needed:

```java
@Test
void shouldReturnEmptyResultsForBlankQuery() {
    KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
    KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
    KnowledgeRetrievalService service = new KnowledgeRetrievalService(
            repository,
            embeddingService,
            new RagProperties(500, 80, 5, 8, 12)
    );

    assertThat(service.retrieve("   ", null, null)).isEmpty();
}
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest test`

Expected: FAIL because `KnowledgeRetrievalService` still delegates to `searchByVector(...)`.

**Step 3: Write minimal implementation**

Update the service:

```java
public List<RetrievedKnowledgeChunk> retrieve(String query, List<String> documentIds, Integer topK) {
    int limit = topK == null || topK <= 0 ? properties.askTopK() : topK;
    if (query == null || query.isBlank()) {
        return List.of();
    }
    float[] queryVector = embeddingService.embed(query);
    return repository.searchHybrid(query, queryVector, documentIds, limit);
}
```

Do not add extra retrieval orchestration logic to the service.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceVectorTest.java
git commit -m "feat: switch retrieval service to hybrid search"
```

### Task 5: Update upload-path assertions and verify end-to-end focused coverage

**Files:**
- Modify: `src/test/java/com/agent/editor/service/KnowledgeBaseServiceMilvusTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Modify: `docs/plans/2026-03-22-hybrid-rag-retrieval-design.md`

**Step 1: Write the failing tests**

Adjust `KnowledgeBaseServiceMilvusTest` so upload-path expectations remain aligned with the new collection name and hybrid persistence contract. Keep the tool-layer tests stable by asserting the external retrieval API has not changed.

If `RetrieveKnowledgeToolTest` or `AgentV2ConfigurationSplitTest` break because of internal repository API changes, update them to continue mocking `KnowledgeRetrievalService.retrieve(...)` only.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=KnowledgeBaseServiceMilvusTest,RetrieveKnowledgeToolTest,AgentV2ConfigurationSplitTest test`

Expected: FAIL only where old collection assumptions or internal behavior checks no longer match hybrid retrieval.

**Step 3: Write minimal implementation**

Keep the upload service contract unchanged. Only refresh assertions and comments where the hybrid persistence behavior is now part of the expected model.

Update the design doc if implementation details differ slightly, for example if `fullText` is derived in the repository rather than explicitly stored in the `KnowledgeChunk` domain model.

**Step 4: Run focused and full verification**

Run focused verification:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusConfigTest,MilvusKnowledgeChunkRepositoryTest,KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest,KnowledgeBaseServiceMilvusTest,RetrieveKnowledgeToolTest,AgentV2ConfigurationSplitTest test
```

Run full verification:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/service/KnowledgeBaseServiceMilvusTest.java src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java docs/plans/2026-03-22-hybrid-rag-retrieval-design.md
git commit -m "test: align rag coverage with hybrid retrieval"
```

### Task 6: Final branch verification and handoff notes

**Files:**
- Modify: `README.md`
- Modify: `src/main/resources/application.yml`

**Step 1: Write the failing documentation check**

List the rollout requirements that are currently undocumented:

- default Milvus collection moved to `knowledge_chunks_v2`
- existing uploaded documents must be re-uploaded
- hybrid lexical scope is limited to `heading + chunkText`

**Step 2: Run a quick grep to verify the notes are missing**

Run: `rg -n "knowledge_chunks_v2|re-upload|hybrid retrieval|heading \\+ chunkText" README.md src/main/resources/application.yml`

Expected: no README coverage yet

**Step 3: Write minimal implementation**

Add a concise README section documenting the migration constraint and lexical scope. Do not add a long operational playbook in this increment.

**Step 4: Run final verification**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
git status --short
```

Expected:

- Maven tests pass
- only intended tracked file changes remain

**Step 5: Commit**

```bash
git add README.md src/main/resources/application.yml
git commit -m "docs: describe hybrid rag rollout constraints"
```

# Personal Knowledge RAG Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a personal-knowledge RAG module for `agent-editor` that ingests user-uploaded files, retrieves relevant chunks, and generates cited answers or writing drafts for resume-style tasks.

**Architecture:** Reuse the existing Spring Boot application, `agent.v2` execution runtime, document tools, and trace infrastructure. Add a knowledge-base ingestion pipeline, index chunks for hybrid retrieval, and expose a RAG service that can answer questions or generate writing grounded in uploaded personal materials with citations.

**Tech Stack:** Spring Boot 3.2, Java 17, LangChain4j, in-memory task/runtime infrastructure, OpenAI-compatible chat + embedding models, document parsing libraries, JUnit + Spring Boot Test

---

## Delivery Scope

- Support `Markdown / TXT` upload for personal knowledge documents in v1
- Parse content and split into metadata-rich chunks
- Add chunk storage and retrieval abstraction for BM25 + vector search
- Support two generation modes: `ask` and `write`
- Return cited source chunks with document name and chunk position
- Add a small evaluation dataset and retrieval metrics
- Keep the first version single-app and avoid multi-agent fan-out
- Keep parser and storage contracts extensible so `PDF / Word` can be added later without changing endpoint shapes

## Two-Week Schedule

- **Week 1:** domain model, upload/indexing pipeline, Markdown/TXT parser/chunker, lexical retrieval, basic cited ask endpoint
- **Week 2:** embeddings, hybrid retrieval, write endpoint, evaluation, trace integration, optional tool wiring, polish

### Task 1: Introduce RAG domain model and configuration

**Files:**
- Create: `src/main/java/com/agent/editor/model/KnowledgeDocument.java`
- Create: `src/main/java/com/agent/editor/model/KnowledgeChunk.java`
- Create: `src/main/java/com/agent/editor/dto/RagCitation.java`
- Create: `src/main/java/com/agent/editor/dto/RagAskRequest.java`
- Create: `src/main/java/com/agent/editor/dto/RagWriteRequest.java`
- Create: `src/main/java/com/agent/editor/dto/RagResponse.java`
- Create: `src/main/java/com/agent/editor/config/RagProperties.java`
- Modify: `src/main/java/com/agent/editor/AiEditorApplication.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/agent/editor/config/RagPropertiesTest.java`

**Step 1: Write the failing test**

```java
@SpringBootTest(properties = {
        "rag.chunk-size=500",
        "rag.chunk-overlap=80",
        "rag.ask-top-k=5"
})
class RagPropertiesTest {

    @Autowired
    private RagProperties ragProperties;

    @Test
    void shouldBindRagProperties() {
        assertEquals(500, ragProperties.chunkSize());
        assertEquals(80, ragProperties.chunkOverlap());
        assertEquals(5, ragProperties.askTopK());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RagPropertiesTest test`
Expected: FAIL because `RagProperties` does not exist and no `rag.*` binding is configured.

**Step 3: Write minimal implementation**

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiEditorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEditorApplication.class, args);
    }
}
```

```java
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int askTopK,
        int writeTopK,
        int retrieveTopK
) {
    public RagProperties {
        chunkSize = chunkSize == 0 ? 500 : chunkSize;
        chunkOverlap = chunkOverlap == 0 ? 80 : chunkOverlap;
        askTopK = askTopK == 0 ? 5 : askTopK;
        writeTopK = writeTopK == 0 ? 8 : writeTopK;
        retrieveTopK = retrieveTopK == 0 ? 12 : retrieveTopK;
    }
}
```

Add DTOs with only the fields required for MVP.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RagPropertiesTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/model src/main/java/com/agent/editor/dto src/main/java/com/agent/editor/config/RagProperties.java src/main/java/com/agent/editor/AiEditorApplication.java src/main/resources/application.yml src/test/java/com/agent/editor/config/RagPropertiesTest.java
git commit -m "feat: add rag domain model and config"
```

### Task 2: Add knowledge-base storage and upload skeleton

**Files:**
- Create: `src/main/java/com/agent/editor/service/KnowledgeBaseService.java`
- Create: `src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java`
- Create: `src/main/java/com/agent/editor/controller/KnowledgeBaseController.java`
- Modify: `src/main/java/com/agent/editor/config/OpenApiConfig.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeBaseServiceTest.java`
- Test: `src/test/java/com/agent/editor/controller/KnowledgeBaseControllerTest.java`

**Step 1: Write the failing test**

```java
class KnowledgeBaseServiceTest {

    @Test
    void shouldCreatePendingKnowledgeDocumentRecord() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeBaseService service = new KnowledgeBaseService(store, null, null, null);
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", "# Resume".getBytes());

        KnowledgeDocument document = service.upload(file, "resume");

        assertEquals("resume.md", document.fileName());
        assertEquals("resume", document.category());
        assertEquals("PENDING", document.status());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeBaseServiceTest test`
Expected: FAIL because storage and service do not exist.

**Step 3: Write minimal implementation**

```java
public KnowledgeDocument upload(MultipartFile file, String category) {
    KnowledgeDocument document = new KnowledgeDocument(
            UUID.randomUUID().toString(),
            file.getOriginalFilename(),
            category,
            "PENDING",
            Instant.now()
    );
    store.saveDocument(document);
    return document;
}
```

Expose minimal controller endpoint:

```java
@PostMapping("/api/v1/knowledge/documents")
public KnowledgeDocument upload(@RequestParam("file") MultipartFile file,
                                @RequestParam("category") String category) {
    return knowledgeBaseService.upload(file, category);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=KnowledgeBaseServiceTest,KnowledgeBaseControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/KnowledgeBaseService.java src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java src/main/java/com/agent/editor/controller/KnowledgeBaseController.java src/test/java/com/agent/editor/service/KnowledgeBaseServiceTest.java src/test/java/com/agent/editor/controller/KnowledgeBaseControllerTest.java
git commit -m "feat: add knowledge-base upload skeleton"
```

### Task 3: Parse Markdown/TXT files and split them into metadata-rich chunks

**Files:**
- Create: `src/main/java/com/agent/editor/service/KnowledgeDocumentParser.java`
- Create: `src/main/java/com/agent/editor/service/KnowledgeChunkSplitter.java`
- Create: `src/main/java/com/agent/editor/model/ParsedKnowledgeDocument.java`
- Modify: `src/main/java/com/agent/editor/service/KnowledgeBaseService.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java`

**Step 1: Write the failing tests**

```java
class KnowledgeChunkSplitterTest {

    @Test
    void shouldSplitByHeadingAndKeepMetadata() {
        RagProperties properties = new RagProperties(500, 80, 5, 8, 12);
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(properties);
        String content = "# 项目经历\nJava Spring Boot ElasticSearch RabbitMQ\n\n# 实习经历\nWebFlux Flowable";

        List<KnowledgeChunk> chunks = splitter.split("doc-1", "resume.md", content, Map.of("category", "resume"));

        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).chunkIndex());
        assertNotNull(chunks.get(0).heading());
    }
}
```

```java
class KnowledgeDocumentParserTest {

    @Test
    void shouldParseMarkdownWithoutExternalOcr() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", "# 项目\n内容".getBytes(StandardCharsets.UTF_8));
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        ParsedKnowledgeDocument parsed = parser.parse(file);

        assertTrue(parsed.content().contains("项目"));
        assertEquals("MARKDOWN", parsed.documentType());
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=KnowledgeChunkSplitterTest,KnowledgeDocumentParserTest test`
Expected: FAIL because parser and splitter do not exist.

**Step 3: Write minimal implementation**

```java
public List<KnowledgeChunk> split(String documentId, String fileName, String content, Map<String, String> metadata) {
    List<String> sections = Arrays.stream(content.split("(?m)^# "))
            .filter(section -> !section.isBlank())
            .toList();
    List<KnowledgeChunk> result = new ArrayList<>();
    for (int i = 0; i < sections.size(); i++) {
        result.add(new KnowledgeChunk(documentId, i, fileName, extractHeading(sections.get(i)), sections.get(i), metadata));
    }
    return result;
}
```

Support `.md` and `.txt` in v1. For `.pdf`, `.doc`, and `.docx`, return a clear unsupported-format error or mark the document as `FAILED` with an explicit reason. Keep parser branching structured so PDF/Word support can be added later without changing callers.

**Step 4: Run tests to verify they pass**

Run: `mvn -Dtest=KnowledgeChunkSplitterTest,KnowledgeDocumentParserTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/KnowledgeDocumentParser.java src/main/java/com/agent/editor/service/KnowledgeChunkSplitter.java src/main/java/com/agent/editor/model/ParsedKnowledgeDocument.java src/main/java/com/agent/editor/service/KnowledgeBaseService.java src/test/java/com/agent/editor/service/KnowledgeChunkSplitterTest.java src/test/java/com/agent/editor/service/KnowledgeDocumentParserTest.java
git commit -m "feat: add knowledge parsing and chunk splitting"
```

### Task 4: Add lexical retrieval and chunk search abstraction

**Files:**
- Create: `src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java`
- Create: `src/main/java/com/agent/editor/service/KnowledgeChunkRepository.java`
- Modify: `src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java`
- Modify: `src/main/java/com/agent/editor/service/KnowledgeBaseService.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java`

**Step 1: Write the failing test**

```java
class KnowledgeRetrievalServiceTest {

    @Test
    void shouldReturnChunksOrderedByLexicalScore() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        store.saveChunk(new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of()));
        store.saveChunk(new KnowledgeChunk("doc-1", 1, "resume.md", "其他", "Redis 缓存经验", Map.of()));

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(store, null, new RagProperties(500, 80, 5, 8, 12));
        List<KnowledgeChunk> chunks = service.retrieveForAsk("Spring Boot", List.of("doc-1"));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).chunkText().contains("Spring Boot"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeRetrievalServiceTest test`
Expected: FAIL because retrieval service does not exist.

**Step 3: Write minimal implementation**

```java
public List<KnowledgeChunk> retrieveForAsk(String query, List<String> documentIds) {
    return repository.findByDocumentIds(documentIds).stream()
            .sorted(Comparator.comparingInt(chunk -> -lexicalScore(query, chunk.chunkText())))
            .limit(properties.askTopK())
            .toList();
}
```

Keep the first cut in-memory so the retrieval contract is stable before you swap in Elasticsearch or another backend.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=KnowledgeRetrievalServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java src/main/java/com/agent/editor/service/KnowledgeChunkRepository.java src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java src/main/java/com/agent/editor/service/KnowledgeBaseService.java src/test/java/com/agent/editor/service/KnowledgeRetrievalServiceTest.java
git commit -m "feat: add lexical chunk retrieval for rag"
```

### Task 5: Add embeddings and hybrid retrieval

**Files:**
- Create: `src/main/java/com/agent/editor/service/EmbeddingService.java`
- Create: `src/main/java/com/agent/editor/service/HybridRetrievalMerger.java`
- Modify: `src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java`
- Modify: `src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java`
- Modify: `src/main/java/com/agent/editor/config/LangChainConfig.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/agent/editor/service/HybridRetrievalMergerTest.java`

**Step 1: Write the failing test**

```java
class HybridRetrievalMergerTest {

    @Test
    void shouldMergeKeywordAndVectorResultsByReciprocalRank() {
        KnowledgeChunk keyword1 = new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of());
        KnowledgeChunk vector1 = new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of());
        KnowledgeChunk vector2 = new KnowledgeChunk("doc-2", 0, "intern.md", "实习经历", "LangChain4j Agent 经验", Map.of());

        HybridRetrievalMerger merger = new HybridRetrievalMerger();
        List<KnowledgeChunk> merged = merger.merge(List.of(keyword1), List.of(vector1, vector2), 3);

        assertEquals(2, merged.size());
        assertTrue(merged.get(0).chunkText().contains("Spring Boot"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HybridRetrievalMergerTest test`
Expected: FAIL because hybrid merger does not exist.

**Step 3: Write minimal implementation**

```java
public List<KnowledgeChunk> merge(List<KnowledgeChunk> keywordHits, List<KnowledgeChunk> vectorHits, int limit) {
    Map<String, Double> scores = new HashMap<>();
    addScores(scores, keywordHits, 60);
    addScores(scores, vectorHits, 60);
    return Stream.concat(keywordHits.stream(), vectorHits.stream())
            .collect(Collectors.toMap(this::key, chunk -> chunk, (left, right) -> left))
            .values().stream()
            .sorted(Comparator.comparingDouble(chunk -> -scores.getOrDefault(key(chunk), 0.0)))
            .limit(limit)
            .toList();
}
```

Use the existing embedding-model configuration in `application.yml` and keep the first vector store implementation in-memory if external infrastructure is not ready.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HybridRetrievalMergerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/EmbeddingService.java src/main/java/com/agent/editor/service/HybridRetrievalMerger.java src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java src/main/java/com/agent/editor/service/InMemoryKnowledgeStore.java src/main/java/com/agent/editor/config/LangChainConfig.java src/main/resources/application.yml src/test/java/com/agent/editor/service/HybridRetrievalMergerTest.java
git commit -m "feat: add hybrid retrieval for rag"
```

### Task 6: Assemble task-aware context and cited generation

**Files:**
- Create: `src/main/java/com/agent/editor/service/RagPromptBuilder.java`
- Create: `src/main/java/com/agent/editor/service/RagGenerationService.java`
- Test: `src/test/java/com/agent/editor/service/RagPromptBuilderTest.java`
- Test: `src/test/java/com/agent/editor/service/RagGenerationServiceTest.java`

**Step 1: Write the failing tests**

```java
class RagPromptBuilderTest {

    @Test
    void shouldBuildResumeBulletPromptWithCitations() {
        RagPromptBuilder builder = new RagPromptBuilder();
        List<KnowledgeChunk> chunks = List.of(new KnowledgeChunk("doc-1", 0, "project.md", "项目经历", "负责 OCR 异步化与限流", Map.of()));

        String prompt = builder.buildWritePrompt("resume_bullets", "生成 3 条后端简历亮点", chunks);

        assertTrue(prompt.contains("resume_bullets"));
        assertTrue(prompt.contains("OCR 异步化与限流"));
        assertTrue(prompt.contains("必须给出引用编号"));
    }
}
```

```java
class RagGenerationServiceTest {

    @Test
    void shouldReturnAnswerWithCitations() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieveForAsk(anyString(), anyList()))
                .thenReturn(List.of(new KnowledgeChunk("doc-1", 0, "intern.md", "实习经历", "在小米实习做过 WebFlux 流式推送", Map.of())));
        RecordingChatModel model = new RecordingChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("你做过 WebFlux 流式推送。[1]"))
                .build());

        RagGenerationService service = new RagGenerationService(retrievalService, model, new RagPromptBuilder());
        RagResponse response = service.ask("我做过什么流式推送相关工作", List.of("doc-1"));

        assertTrue(response.answer().contains("[1]"));
        assertEquals(1, response.citations().size());
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatResponse response;

        private RecordingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return response;
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=RagPromptBuilderTest,RagGenerationServiceTest test`
Expected: FAIL because prompt builder and generation service do not exist.

**Step 3: Write minimal implementation**

```java
public RagResponse ask(String question, List<String> documentIds) {
    List<KnowledgeChunk> chunks = retrievalService.retrieveForAsk(question, documentIds);
    String prompt = promptBuilder.buildAskPrompt(question, chunks);
    ChatResponse response = chatModel.chat(ChatRequest.builder()
            .messages(List.of(UserMessage.from(prompt)))
            .build());
    String answer = response.aiMessage() == null ? "" : response.aiMessage().text();
    return new RagResponse(answer, toCitations(chunks));
}
```

Use the repo's existing `ChatModel` abstraction instead of introducing `ChatLanguageModel`. If you later want a higher-level adapter, add it behind `RagGenerationService` without changing the controller contract.

**Step 4: Run tests to verify they pass**

Run: `mvn -Dtest=RagPromptBuilderTest,RagGenerationServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/RagPromptBuilder.java src/main/java/com/agent/editor/service/RagGenerationService.java src/test/java/com/agent/editor/service/RagPromptBuilderTest.java src/test/java/com/agent/editor/service/RagGenerationServiceTest.java
git commit -m "feat: add cited rag generation"
```

### Task 7: Expose ask/write endpoints

**Files:**
- Create: `src/main/java/com/agent/editor/controller/RagController.java`
- Test: `src/test/java/com/agent/editor/controller/RagControllerTest.java`

**Step 1: Write the failing test**

```java
@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagGenerationService ragGenerationService;

    @Test
    void shouldReturnWriteResponseWithCitations() throws Exception {
        RagResponse response = new RagResponse("你可以这样写项目亮点。[1]", List.of(new RagCitation(1, "project.md", 0, "负责 OCR 异步化与限流")));
        when(ragGenerationService.write(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/rag/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"生成三条简历亮点\",\"taskType\":\"resume_bullets\",\"documentIds\":[\"doc-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("[1]")))
                .andExpect(jsonPath("$.citations[0].fileName").value("project.md"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RagControllerTest test`
Expected: FAIL because the RAG endpoints are not implemented.

**Step 3: Write minimal implementation**

```java
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagGenerationService ragGenerationService;

    public RagController(RagGenerationService ragGenerationService) {
        this.ragGenerationService = ragGenerationService;
    }

    @PostMapping("/write")
    public RagResponse write(@RequestBody RagWriteRequest request) {
        return ragGenerationService.write(request);
    }

    @PostMapping("/ask")
    public RagResponse ask(@RequestBody RagAskRequest request) {
        return ragGenerationService.ask(request.question(), request.documentIds());
    }
}
```

Use a dedicated `RagController` instead of modifying `AgentController`. The existing `AgentController` is already bound to `/api/v1/agent`, so keeping RAG endpoints in a separate controller preserves the public `/api/v1/rag/*` contract cleanly.

**Step 4: Run tests to verify it passes**

Run: `mvn -Dtest=RagControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/RagController.java src/test/java/com/agent/editor/controller/RagControllerTest.java
git commit -m "feat: expose rag endpoints"
```

### Task 8: Optional stretch task for agent.v2 tool integration

Only start this task after `/api/v1/knowledge/*` and `/api/v1/rag/*` are working end-to-end.

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java`

**Step 1: Write the failing test**

```java
class RetrieveKnowledgeToolTest {

    @Test
    void shouldReturnRetrievedChunkSummary() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieveForAsk("Spring", List.of("doc-1")))
                .thenReturn(List.of(new KnowledgeChunk("doc-1", 0, "resume.md", "项目经历", "Spring Boot 项目经验", Map.of())));
        RetrieveKnowledgeTool tool = new RetrieveKnowledgeTool(retrievalService);

        ToolResult result = tool.execute(
                new ToolInvocation("retrieveKnowledge", "{\"query\":\"Spring\",\"documentIds\":[\"doc-1\"]}"),
                new ToolContext("task-1", null)
        );

        assertTrue(result.message().contains("Spring Boot 项目经验"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RetrieveKnowledgeToolTest test`
Expected: FAIL because the retrieval tool is not implemented.

**Step 3: Write minimal implementation**

```java
@Override
public String name() {
    return "retrieveKnowledge";
}
```

Register the tool in `ToolConfig` only if `KnowledgeRetrievalService` is available. Keep the primary delivery endpoint-first even if this stretch task slips.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RetrieveKnowledgeToolTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java src/main/java/com/agent/editor/config/ToolConfig.java src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java
git commit -m "feat: add retrieve knowledge tool"
```

### Task 9: Add evaluation and trace observability

**Files:**
- Create: `src/main/java/com/agent/editor/service/RagEvaluationService.java`
- Create: `src/test/resources/rag/personal-kb-eval.json`
- Create: `src/test/java/com/agent/editor/service/RagEvaluationServiceTest.java`
- Create: `src/test/java/com/agent/editor/service/RagTraceInstrumentationTest.java`
- Modify: `src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java`
- Modify: `src/main/java/com/agent/editor/service/RagGenerationService.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/trace/TraceCategory.java`

**Step 1: Write the failing test**

```java
class RagEvaluationServiceTest {

    @Test
    void shouldComputeHitAtThree() {
        RagEvaluationService service = new RagEvaluationService();
        List<String> retrieved = List.of("doc-2#0", "doc-1#1", "doc-3#0");
        double hitAt3 = service.hitAtK(retrieved, Set.of("doc-1#1"), 3);

        assertEquals(1.0, hitAt3, 0.001);
    }
}
```

```java
class RagTraceInstrumentationTest {

    @Test
    void shouldEmitRetrievalTraceWithMergedChunkIds() {
        InMemoryTraceStore traceStore = new InMemoryTraceStore();
        TraceCollector traceCollector = new DefaultTraceCollector(traceStore);

        // build retrieval/generation service with stubbed data and execute one ask flow

        assertTrue(traceStore.getByTaskId("task-rag-1").stream().anyMatch(trace ->
                trace.category() == TraceCategory.KNOWLEDGE_RETRIEVAL
                        && "rag.retrieve.completed".equals(trace.stage())
                        && trace.payload().containsKey("mergedChunkIds")));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RagEvaluationServiceTest,RagTraceInstrumentationTest test`
Expected: FAIL because the evaluation service and retrieval trace instrumentation do not exist.

**Step 3: Write minimal implementation**

```java
public double hitAtK(List<String> retrieved, Set<String> relevant, int k) {
    return retrieved.stream().limit(k).anyMatch(relevant::contains) ? 1.0 : 0.0;
}
```

```java
traceCollector.collect(new TraceRecord(
        UUID.randomUUID().toString(),
        taskId,
        Instant.now(),
        TraceCategory.KNOWLEDGE_RETRIEVAL,
        "rag.retrieve.completed",
        null,
        null,
        null,
        Map.of("mergedChunkIds", mergedChunkIds, "citationIds", citationIds)
));
```

Emit trace records from `KnowledgeRetrievalService` and `RagGenerationService`, not from `DefaultTraceCollector`. Add a dedicated `TraceCategory.KNOWLEDGE_RETRIEVAL` and include retrieval count, lexical hit ids, vector hit ids, merged chunk ids, and citation ids in payloads so the demo page can show why a response was grounded.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=RagEvaluationServiceTest,RagTraceInstrumentationTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/RagEvaluationService.java src/test/resources/rag/personal-kb-eval.json src/test/java/com/agent/editor/service/RagEvaluationServiceTest.java src/test/java/com/agent/editor/service/RagTraceInstrumentationTest.java src/main/java/com/agent/editor/service/KnowledgeRetrievalService.java src/main/java/com/agent/editor/service/RagGenerationService.java src/main/java/com/agent/editor/agent/v2/trace/TraceCategory.java
git commit -m "feat: add rag evaluation and trace observability"
```

## Acceptance Checklist

- User can upload a Markdown or TXT personal knowledge document and see a persisted document record in app memory
- Parsed content is chunked with heading and category metadata
- `/api/v1/rag/ask` returns answer plus citations
- `/api/v1/rag/write` returns writing draft plus citations
- Retrieval logs or trace records show lexical hits, vector hits, and merged hits
- Evaluation test covers at least `Hit@3`
- Optional: `retrieveKnowledge` tool is registered for agent-driven retrieval

## Risks and Deferrals

- If external vector infrastructure is not ready, keep the vector index in memory first and finish the retrieval contract plus evaluation before swapping backends.
- PDF/Word parsing is explicitly deferred from v1. Keep upload and parser contracts ready for a follow-up task instead of partially implementing binary parsing now.
- Do not add multi-agent orchestration in this branch. Keep the “agentic” behavior limited to optional retrieval-tool invocation and second-pass query rewrite.
- Do not stream RAG responses in the first iteration. Ship JSON responses first so citation shape, error handling, and trace output stay simple.
- Prefer endpoint-first delivery before demo-page integration.

## Demo Script

1. Upload `resume.md`, `internship.txt`, and `project-notes.md`
2. Call `/api/v1/rag/ask` with `我做过哪些和流式处理相关的工作`
3. Call `/api/v1/rag/write` with `生成三条适合 Java 后端岗位的简历亮点`
4. Show citations pointing back to uploaded files
5. Show trace output and evaluation metrics

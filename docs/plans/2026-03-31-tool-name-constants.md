# Tool Name Constants Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace raw document-tool name strings in production code with shared constants, and update directly related tests to use the same source of truth.

**Architecture:** Add a centralized `DocumentToolNames` constants class in the document tool package. Refactor tool implementations, orchestrators, supervisor configuration, and directly related tests to reference these constants instead of repeating raw strings, while preserving existing behavior and ordered tool lists.

**Tech Stack:** Java 17, Spring Boot, JUnit 5

---

### Task 1: Lock Down the New Constants Entry Point

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolNames.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing tests**

Change targeted tests so they import and assert against `DocumentToolNames` constants for:

- `retrieveKnowledge`
- `appendToDocument`
- `getDocumentSnapshot`
- `searchContent`
- `analyzeDocument`
- `editDocument`

Example:

```java
assertThat(toolRegistry.get(DocumentToolNames.RETRIEVE_KNOWLEDGE)).isNotNull();
assertEquals(
        List.of(
                DocumentToolNames.EDIT_DOCUMENT,
                DocumentToolNames.APPEND_TO_DOCUMENT,
                DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                DocumentToolNames.SEARCH_CONTENT
        ),
        runtime.actorAllowedTools.get(0)
);
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,ReflexionOrchestratorTest test`

Expected: FAIL because `DocumentToolNames` does not exist yet.

**Step 3: Write minimal implementation**

Create `DocumentToolNames`:

```java
public final class DocumentToolNames {
    public static final String EDIT_DOCUMENT = "editDocument";
    public static final String APPEND_TO_DOCUMENT = "appendToDocument";
    public static final String GET_DOCUMENT_SNAPSHOT = "getDocumentSnapshot";
    public static final String SEARCH_CONTENT = "searchContent";
    public static final String ANALYZE_DOCUMENT = "analyzeDocument";
    public static final String RETRIEVE_KNOWLEDGE = "retrieveKnowledge";
}
```

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,ReflexionOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolNames.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java
git commit -m "refactor: add document tool name constants"
```

### Task 2: Replace Production String Literals

**Files:**
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/EditDocumentTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/AppendToDocumentTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/GetDocumentSnapshotTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/SearchContentTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/AnalyzeDocumentTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`

**Step 1: Write the failing test**

Update one focused researcher test to assert against `DocumentToolNames.RETRIEVE_KNOWLEDGE`, for example:

```java
assertEquals(DocumentToolNames.RETRIEVE_KNOWLEDGE, toolCalls.getCalls().get(0).getName());
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest test`

Expected: FAIL until production code and tests consistently reference the new constants.

**Step 3: Write minimal implementation**

Refactor production code so all affected tool-name identifiers use `DocumentToolNames.*`. Preserve behavior and list ordering exactly.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/EditDocumentTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/AppendToDocumentTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/GetDocumentSnapshotTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/SearchContentTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/AnalyzeDocumentTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeTool.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java
git commit -m "refactor: replace document tool literals in production code"
```

### Task 3: Update Directly Related Tests and Verify

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentTest.java`
- Modify: direct document tool tests under `src/test/java/com/agent/editor/agent/v2/tool/document/`
- Verify: related tests already touched above

**Step 1: Write the failing tests**

Update directly related tests to import and use `DocumentToolNames` wherever they are asserting these identifiers as shared tool names.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=GroundedWriterAgentTest,EvidenceReviewerAgentTest,ResearcherAgentContextFactoryTest,EditDocumentToolTest,AppendToDocumentToolTest,GetDocumentSnapshotToolTest,SearchContentToolTest,AnalyzeDocumentToolTest,RetrieveKnowledgeToolTest test`

Expected: FAIL until all directly related tests are updated consistently.

**Step 3: Write minimal implementation**

Replace raw literals in those tests with `DocumentToolNames` constants. Leave unrelated display/event strings unchanged.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,ReflexionOrchestratorTest,ResearcherAgentTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,EditDocumentToolTest,AppendToDocumentToolTest,GetDocumentSnapshotToolTest,SearchContentToolTest,AnalyzeDocumentToolTest,RetrieveKnowledgeToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/EditDocumentToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/AppendToDocumentToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/GetDocumentSnapshotToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/SearchContentToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/AnalyzeDocumentToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/RetrieveKnowledgeToolTest.java \
        docs/plans/2026-03-31-tool-name-constants.md
git commit -m "test: align document tool tests with shared constants"
```

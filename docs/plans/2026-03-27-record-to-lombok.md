# Record To Lombok Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace every Java `record` in production and test code with Lombok-backed Java classes using standard JavaBean accessors.

**Architecture:** Migrate records in small batches so compilation and behavior regressions stay local. Keep framework-facing types bindable with no-arg constructors and setters, while preserving any custom constructor normalization logic through hand-written constructors or setters where needed.

**Tech Stack:** Java 17, Maven, Spring Boot 3.2, Lombok, JUnit 5, Mockito.

---

### Task 1: Lock binding and DTO behavior with focused tests

**Files:**
- Modify: `src/test/java/com/agent/editor/config/MilvusPropertiesTest.java`
- Modify: `src/test/java/com/agent/editor/config/RagPropertiesTest.java`
- Modify: `src/test/java/com/agent/editor/controller/KnowledgeBaseControllerTest.java`
- Modify: `src/test/java/com/agent/editor/controller/TraceControllerTest.java`

**Step 1: Write the failing test**

Adjust the existing tests so they assert through JavaBean accessors such as `getHost()`, `getPort()`, `getAnswer()`, `getCitations()`, and any response DTO getters that will replace record accessors.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=MilvusPropertiesTest,RagPropertiesTest,KnowledgeBaseControllerTest,TraceControllerTest test`

Expected: compilation fails because the target classes still expose `record` accessors.

**Step 3: Write minimal implementation**

Convert the directly referenced production `record` types in `config` and `dto` packages to Lombok JavaBean classes with the constructors required for Spring and Jackson.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=MilvusPropertiesTest,RagPropertiesTest,KnowledgeBaseControllerTest,TraceControllerTest test`

Expected: tests pass and Spring properties binding still works.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config src/main/java/com/agent/editor/dto src/test/java/com/agent/editor/config src/test/java/com/agent/editor/controller
git commit -m "refactor: migrate config and dto records"
```

### Task 2: Migrate plain model records and their tests

**Files:**
- Modify: `src/main/java/com/agent/editor/model/KnowledgeDocument.java`
- Modify: `src/main/java/com/agent/editor/model/KnowledgeChunk.java`
- Modify: `src/main/java/com/agent/editor/model/ParsedKnowledgeDocument.java`
- Modify: `src/main/java/com/agent/editor/model/RetrievedKnowledgeChunk.java`
- Modify: `src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionDocument.java`
- Modify: `src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionNode.java`
- Modify: `src/main/java/com/agent/editor/utils/rag/pdf/PdfTextLine.java`
- Modify: related tests under `src/test/java/com/agent/editor/service`

**Step 1: Write the failing test**

Update the affected service tests to use JavaBean getters for the model types that currently expose `record` accessors.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=KnowledgeDocumentParserTest,KnowledgeChunkSplitterTest,KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest test`

Expected: compilation fails until the model classes are migrated.

**Step 3: Write minimal implementation**

Convert the model and parsing helper records to Lombok-backed classes, adding no-arg and all-arg constructors where framework or test construction needs them.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=KnowledgeDocumentParserTest,KnowledgeChunkSplitterTest,KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest test`

Expected: tests pass with no parsing or equality regressions.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/model src/main/java/com/agent/editor/utils/rag src/test/java/com/agent/editor/service
git commit -m "refactor: migrate model records"
```

### Task 3: Migrate agent v2 top-level records with constructor guards preserved

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/state/DocumentSnapshot.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/state/TaskState.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionResult.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionStateSnapshot.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/AgentRunContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/event/ExecutionEvent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritique.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ReviewerFeedback.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/WorkerDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/WorkerResult.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidencePackage.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/task/TaskRequest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/task/TaskResult.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolInvocation.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolResult.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/trace/TraceRecord.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequestTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/AgentRunContextTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/event/ExecutionEventTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java`

**Step 1: Write the failing test**

Update the focused `agent/v2` tests to use JavaBean getters and to assert constructor guard behavior through public APIs instead of record reflection.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ExecutionRequestTest,AgentRunContextTest,ExecutionEventTest,EvidenceContractsTest test`

Expected: compilation or assertion failures because the runtime types are still records or tests still inspect record metadata.

**Step 3: Write minimal implementation**

Convert the top-level runtime records to classes. Preserve custom normalization such as defensive `List.copyOf(...)`, immutable map wrapping, and `withXxx` state transition helpers.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ExecutionRequestTest,AgentRunContextTest,ExecutionEventTest,EvidenceContractsTest test`

Expected: tests pass and runtime state helpers still behave as before.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2 src/test/java/com/agent/editor/agent/v2
git commit -m "refactor: migrate agent runtime records"
```

### Task 4: Migrate sealed and nested record hierarchies

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/agent/Decision.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/memory/ChatMessage.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/memory/ChatTranscriptMemory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorDecision.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningResponse.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/SupervisorRoutingResponse.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/agent/DecisionTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ExecutionMemoryChatMessageMapperTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Update the focused tests to instantiate nested classes directly and assert through JavaBean getters.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DecisionTest,ExecutionMemoryChatMessageMapperTest,ToolLoopExecutionRuntimeTest test`

Expected: compilation fails while the nested hierarchies still use `record`.

**Step 3: Write minimal implementation**

Replace nested `record` declarations with nested static classes, update `permits` clauses, and migrate internal call sites from `xxx()` to `getXxx()`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DecisionTest,ExecutionMemoryChatMessageMapperTest,ToolLoopExecutionRuntimeTest test`

Expected: tests pass and sealed hierarchy dispatch still works.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2 src/test/java/com/agent/editor/agent/v2
git commit -m "refactor: migrate nested record hierarchies"
```

### Task 5: Replace remaining records and test helpers

**Files:**
- Modify: all remaining files reported by `rg -n "\\brecord\\b" src/main/java src/test/java`
- Modify: `src/test/java/com/agent/editor/service/KnowledgeEmbeddingBadCaseTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ExecutionMemoryChatMessageMapperTest.java`

**Step 1: Write the failing test**

Update any remaining tests that still depend on `record` accessors or local private `record` declarations.

**Step 2: Run test to verify it fails**

Run: `rg -n "\\brecord\\b" src/main/java src/test/java`

Expected: at least one remaining `record` occurrence or compilation issue still exists.

**Step 3: Write minimal implementation**

Convert the remaining production and test `record` declarations to classes and update all remaining accessor usages.

**Step 4: Run test to verify it passes**

Run: `rg -n "\\brecord\\b" src/main/java src/test/java`

Expected: no `record` declarations remain in source or test code, aside from incidental text in comments or assertions if intentionally retained.

**Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: remove remaining record usage"
```

### Task 6: Full verification

**Files:**
- Modify: any files needed to fix regressions discovered by the full suite

**Step 1: Write the failing test**

If the full suite reveals a regression, first add or tighten the narrowest failing test around that regression before changing production code.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test`

Expected: either full green, or a concrete failing test that reveals a missed migration path.

**Step 3: Write minimal implementation**

Fix only the regressions exposed by the suite, preserving the chosen JavaBean + Lombok style.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test`

Expected: full suite passes with no remaining `record` declarations.

**Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "test: verify record to lombok migration"
```

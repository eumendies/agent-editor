# Agent Memory Tool-Driven Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the pending-confirmation long-term-memory flow with a tool-driven model where the main agent can search, create, replace, and delete persisted memories directly, while users can also manage `USER_PROFILE` memories through HTTP APIs.

**Architecture:** Keep Milvus as the confirmed long-term-memory store, keep short-term transcript memory unchanged, inject `USER_PROFILE` during task initialization, retain `memory_search` for retrieval, add `memory_upsert` for direct mutation, and remove post-task extraction plus pending-candidate review from the runtime.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Milvus, JUnit 5, Mockito

---

### Task 1: Refactor Long-Term Memory Repository For Direct Writes

**Files:**
- Modify: `src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java`
- Modify: `src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java`
- Modify: `src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java`
- Modify: `src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java`
- Modify: `src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- repository can `createMemory(LongTermMemoryItem item)`
- repository can `deleteMemory(String memoryId)`
- repository can load a memory by `memoryId` for replace flows
- retrieval results expose `memoryId` so later tool calls can target an existing memory

Use assertions like:

```java
assertThat(result.getMemoryId()).isEqualTo("memory-123");
assertThat(repository.findById("memory-123")).isPresent();
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest,LongTermMemoryRetrievalServiceTest test`

Expected: FAIL because the repository does not yet support the direct-write contract.

**Step 3: Write minimal implementation**

- Expand `LongTermMemoryRepository` with:

```java
Optional<LongTermMemoryItem> findById(String memoryId);
LongTermMemoryItem createMemory(LongTermMemoryItem item);
void deleteMemory(String memoryId);
List<LongTermMemoryItem> listUserProfiles();
```

- Update `MilvusLongTermMemoryRepository` to implement the new methods.
- Make `RetrievedLongTermMemory` include `memoryId`.
- Add concise Chinese comments around delete-plus-insert assumptions so replace semantics stay reviewable.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest,LongTermMemoryRetrievalServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java \
        src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java \
        src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java \
        src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java \
        src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java
git commit -m "refactor: support direct long-term memory writes"
```

### Task 2: Add Long-Term Memory Write Service And memory_upsert Tool

**Files:**
- Create: `src/main/java/com/agent/editor/service/LongTermMemoryWriteService.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertAction.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Create: `src/test/java/com/agent/editor/service/LongTermMemoryWriteServiceTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- `CREATE USER_PROFILE` succeeds without `documentId`
- `CREATE DOCUMENT_DECISION` fails without `documentId`
- `REPLACE` performs delete-plus-insert against an existing `memoryId`
- `DELETE` removes the targeted memory
- tool output clearly reports the performed action and resulting memory id

Example assertions:

```java
assertThatThrownBy(() -> service.createDocumentDecision(null, "Keep section 3"))
        .isInstanceOf(IllegalArgumentException.class);

assertThat(result.getAction()).isEqualTo("REPLACE");
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryWriteServiceTest,MemoryUpsertToolTest test`

Expected: FAIL because the write service and upsert tool do not exist yet.

**Step 3: Write minimal implementation**

- Create `LongTermMemoryWriteService` as the single application-layer mutation entry point.
- Validate tool inputs strictly:

```java
if (memoryType == DOCUMENT_DECISION && action == CREATE && StringUtils.isBlank(documentId)) {
    throw new IllegalArgumentException("documentId is required for document decisions");
}
```

- Implement replace semantics as:

```java
LongTermMemoryItem existing = repository.findById(memoryId).orElseThrow(...);
repository.deleteMemory(existing.getMemoryId());
return repository.createMemory(newItem);
```

- Register `MemoryUpsertTool` in `ToolConfig`, and document in the tool description that only explicit user statements or explicit conflicts should trigger writes.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryWriteServiceTest,MemoryUpsertToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/LongTermMemoryWriteService.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertAction.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertArguments.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java \
        src/main/java/com/agent/editor/config/ToolConfig.java \
        src/test/java/com/agent/editor/service/LongTermMemoryWriteServiceTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java
git commit -m "feat: add direct long-term memory upsert tool"
```

### Task 3: Add USER_PROFILE Management APIs

**Files:**
- Modify: `src/main/java/com/agent/editor/controller/LongTermMemoryController.java`
- Create: `src/main/java/com/agent/editor/dto/UserProfileMemoryRequest.java`
- Create: `src/main/java/com/agent/editor/dto/UserProfileMemoryResponse.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing tests**

Add controller tests proving:

- `GET /api/v2/memory/profiles` returns persisted profile memories
- `POST /api/v2/memory/profiles` creates a new profile
- `PUT /api/v2/memory/profiles/{memoryId}` replaces an existing profile
- `DELETE /api/v2/memory/profiles/{memoryId}` deletes the profile

Keep response shape intentionally compact:

```json
{
  "memoryId": "memory-123",
  "summary": "Default to Chinese",
  "memoryType": "USER_PROFILE"
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: FAIL because the controller still exposes pending-candidate endpoints.

**Step 3: Write minimal implementation**

- Replace the pending endpoints in `LongTermMemoryController` with profile CRUD endpoints.
- Reuse `LongTermMemoryWriteService` for create, replace, and delete.
- Add a read path through `LongTermMemoryRepository.listUserProfiles()`.
- Keep the HTTP surface limited to `USER_PROFILE`; do not add HTTP APIs for document decisions.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/LongTermMemoryController.java \
        src/main/java/com/agent/editor/dto/UserProfileMemoryRequest.java \
        src/main/java/com/agent/editor/dto/UserProfileMemoryResponse.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: add user profile memory management api"
```

### Task 4: Remove Pending-Candidate Flow From Runtime Contracts

**Files:**
- Delete: `src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java`
- Delete: `src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java`
- Delete: `src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java`
- Delete: `src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Delete: `src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java`

**Step 1: Write the failing tests**

Add or update tests proving:

- `AgentTaskResponse` no longer exposes `pendingMemoryCandidates`
- task submission still succeeds without any pending-memory payload
- no production code references the deleted candidate DTOs

Example assertion:

```java
assertThat(response.getPendingMemoryCandidates()).isNull();
```

If the property is removed entirely, update the tests to assert the serialized response shape instead.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: FAIL because runtime contracts still include the pending-memory path.

**Step 3: Write minimal implementation**

- Remove pending-memory fields from `AgentTaskResponse`.
- Delete the pending-memory DTOs and in-memory pending service.
- Remove any remaining task-application methods that expose pending confirmation behavior.
- Add brief Chinese comments where removal changes the lifecycle expectation, so the runtime contract is obvious in review.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/dto/AgentTaskResponse.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git rm src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java \
       src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java \
       src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java \
       src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java \
       src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java \
       src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java
git commit -m "refactor: remove pending long-term memory review flow"
```

### Task 5: Remove Post-Task Memory Extraction From The Main Flow

**Files:**
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractor.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractionAiService.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractionResponse.java`
- Delete: `src/main/java/com/agent/editor/config/LongTermMemoryExtractorConfig.java`
- Modify: `src/main/resources/application.yml`
- Delete: `src/test/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractorTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing tests**

Add or update tests proving:

- task completion no longer triggers any extractor dependency
- application startup no longer requires the extractor-specific model configuration
- existing task flows still complete successfully with profile injection and memory tools available

Example Mockito assertion:

```java
verifyNoInteractions(longTermMemoryExtractor);
```

If the extractor dependency is removed entirely, update constructor and context tests accordingly.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: FAIL because task completion still references the extraction path.

**Step 3: Write minimal implementation**

- Remove extractor invocation from `TaskApplicationService`.
- Delete the extractor classes and their configuration.
- Remove the now-unused `agent.long-term-memory.extractor.*` config keys from `application.yml`.
- Keep the main runtime focused on profile injection plus tool-based memory operations only.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/main/resources/application.yml \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git rm src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractor.java \
       src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractionAiService.java \
       src/main/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractionResponse.java \
       src/main/java/com/agent/editor/config/LongTermMemoryExtractorConfig.java \
       src/test/java/com/agent/editor/agent/v2/memory/LongTermMemoryExtractorTest.java
git commit -m "refactor: remove post-task long-term memory extraction"
```

### Task 6: Final Regression And Tooling Verification

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`
- Modify: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing tests**

Add a final focused regression set proving:

- `memory_search` returns `memoryId` and concise summaries
- `memory_upsert` is registered and callable by the main runtime path
- profile CRUD and task execution coexist without pending-memory regressions

Use assertions like:

```java
assertThat(toolResult).contains("memoryId");
assertThat(toolResult).contains("DOCUMENT_DECISION");
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemorySearchToolTest,MemoryUpsertToolTest,LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: FAIL until all contracts are aligned with the new tool-driven design.

**Step 3: Write minimal implementation**

- Tighten any remaining tool descriptions, response shapes, or Spring wiring issues.
- Remove stale assertions tied to pending-memory behavior.
- Keep the fixes narrowly scoped to contract alignment, not feature expansion.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemorySearchToolTest,MemoryUpsertToolTest,LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Run the full suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS with no regressions in unrelated agent flows.

**Step 6: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java \
        src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "test: align long-term memory regressions with tool-driven flow"
```

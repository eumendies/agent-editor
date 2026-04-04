# Agent Memory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal long-term memory layer for `USER_PROFILE` and `TASK_DECISION` while keeping short-term session memory in-process only.

**Architecture:** Introduce a dedicated long-term-memory domain model, a separate Milvus-backed repository for confirmed memories, an in-memory pending-candidate store for post-task review, automatic runtime loading for confirmed `USER_PROFILE`, and a `memory_search` tool for on-demand `TASK_DECISION` retrieval.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Milvus, JUnit 5, Mockito

---

### Task 1: Add Long-Term Memory Domain Types

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryItem.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryType.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryScopeType.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java`
- Create: `src/test/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryItemTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryItemTest.java`

**Step 1: Write the failing test**

Add tests proving:

- `LongTermMemoryItem` defensively copies list fields
- `USER_PROFILE` can use `PROFILE/default`
- `TASK_DECISION` can carry `documentId`, `sourceTaskId`, and `sourceSessionId`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryItemTest test`

Expected: FAIL because the long-term-memory types do not exist yet.

**Step 3: Write minimal implementation**

Create the domain model with Lombok bean classes and enums. Keep the item model intentionally small:

- `memoryId`
- `memoryType`
- `scopeType`
- `scopeKey`
- `documentId`
- `summary`
- `details`
- `sourceTaskId`
- `sourceSessionId`
- `createdAt`
- `updatedAt`
- `embedding`

Add Javadoc on the public model types because they are workflow entry-point data contracts.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryItemTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryItem.java \
        src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryType.java \
        src/main/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryScopeType.java \
        src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java \
        src/test/java/com/agent/editor/agent/v2/core/memory/LongTermMemoryItemTest.java
git commit -m "feat: add long-term memory domain model"
```

### Task 2: Add Repository And Pending Store Abstractions

**Files:**
- Create: `src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java`
- Create: `src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java`
- Create: `src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java`
- Test: `src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java`

**Step 1: Write the failing test**

Add tests proving `PendingLongTermMemoryService` can:

- save candidate memories by `taskId`
- return the latest candidates for a task
- confirm selected candidates by removing them from the pending store
- discard a task's candidates without affecting other tasks

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PendingLongTermMemoryServiceTest test`

Expected: FAIL because the pending service does not exist yet.

**Step 3: Write minimal implementation**

- Define `LongTermMemoryRepository` with focused operations for:
  - saving confirmed memories
  - loading confirmed `USER_PROFILE` by scope
  - searching confirmed `TASK_DECISION` by scope and query vector
- Implement `PendingLongTermMemoryService` as an in-memory temporary candidate store keyed by `taskId`
- Add brief Chinese comments around overwrite/remove behavior so the review surface is obvious

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PendingLongTermMemoryServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java \
        src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java \
        src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java
git commit -m "feat: add pending long-term memory service"
```

### Task 3: Add Milvus Storage For Confirmed Long-Term Memory

**Files:**
- Create: `src/main/java/com/agent/editor/config/LongTermMemoryMilvusProperties.java`
- Create: `src/main/java/com/agent/editor/config/LongTermMemoryMilvusConfig.java`
- Create: `src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java`
- Create: `src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java`
- Test: `src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java`
- Modify: `src/main/resources/application.yml`

**Step 1: Write the failing test**

Add focused repository tests that prove row mapping for:

- confirmed `USER_PROFILE` write payloads
- confirmed `TASK_DECISION` write payloads
- `documentId`-scoped search filter assembly

Do not write an integration test against a live Milvus instance in this task.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest test`

Expected: FAIL because the repository and config do not exist yet.

**Step 3: Write minimal implementation**

- Add dedicated config/properties for the long-term-memory collection
- Create a separate Milvus collection instead of reusing the knowledge-chunk collection
- Store only confirmed memories
- Reuse the existing Milvus client bean if appropriate, but keep the repository separate from `KnowledgeChunkRepository`

Include Javadoc on repository methods and concise Chinese comments around filter-building and collection-shape assumptions.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/LongTermMemoryMilvusProperties.java \
        src/main/java/com/agent/editor/config/LongTermMemoryMilvusConfig.java \
        src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java \
        src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java \
        src/main/resources/application.yml
git commit -m "feat: add milvus storage for long-term memory"
```

### Task 4: Add Retrieval Services And memory_search Tool

**Files:**
- Create: `src/main/java/com/agent/editor/service/LongTermMemoryRetrievalService.java`
- Create: `src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java`
- Create: `src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Test: `src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- retrieval service loads confirmed `USER_PROFILE` by exact scope
- retrieval service searches confirmed `TASK_DECISION` with `documentId` filtering plus embedding lookup
- `MemorySearchTool` serializes concise result cards rather than raw internal entities

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryRetrievalServiceTest,MemorySearchToolTest test`

Expected: FAIL because the retrieval service and tool do not exist yet.

**Step 3: Write minimal implementation**

- Add retrieval service methods for:
  - `loadConfirmedProfiles()`
  - `searchConfirmedTaskDecisions(String query, String documentId, Integer topK)`
- Add `MemorySearchTool` with a narrow schema:
  - `query`
  - `documentId`
  - `topK`
- Register the tool in `ToolConfig`
- Keep tool descriptions explicit about when to use prior-decision search

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryRetrievalServiceTest,MemorySearchToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/LongTermMemoryRetrievalService.java \
        src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchArguments.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java \
        src/main/java/com/agent/editor/config/ToolConfig.java \
        src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java
git commit -m "feat: add task-decision memory search tool"
```

### Task 5: Inject Confirmed Profiles Into Task Initialization

**Files:**
- Create: `src/main/java/com/agent/editor/service/UserProfilePromptAssembler.java`
- Create: `src/test/java/com/agent/editor/service/UserProfilePromptAssemblerTest.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/task/TaskRequest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`
- Test: `src/test/java/com/agent/editor/service/UserProfilePromptAssemblerTest.java`
- Test: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- confirmed profiles are assembled into a compact prompt block
- task submission includes that prompt block before execution starts
- an empty confirmed-profile set does not change the existing behavior

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=UserProfilePromptAssemblerTest,TaskApplicationServiceTest test`

Expected: FAIL because task initialization does not yet inject long-term profile guidance.

**Step 3: Write minimal implementation**

- Add a small assembler that turns confirmed profiles into a short context string
- Extend `TaskRequest` and `ExecutionRequest` with a dedicated profile-guidance field instead of mutating transcript memory
- Update `TaskApplicationService` to load profiles and pass the assembled guidance into execution

Keep Javadocs updated on the task/runtime entry types because this changes their execution contract.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=UserProfilePromptAssemblerTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/UserProfilePromptAssembler.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/main/java/com/agent/editor/agent/v2/task/TaskRequest.java \
        src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java \
        src/test/java/com/agent/editor/service/UserProfilePromptAssemblerTest.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: inject confirmed profile memory at task start"
```

### Task 6: Extract Candidate Memories After Task Completion

**Files:**
- Create: `src/main/java/com/agent/editor/service/LongTermMemoryExtractor.java`
- Create: `src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java`
- Create: `src/test/java/com/agent/editor/service/LongTermMemoryExtractorTest.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`
- Test: `src/test/java/com/agent/editor/service/LongTermMemoryExtractorTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- extractor can produce `USER_PROFILE` candidates only for stable preference phrasing
- extractor can produce `TASK_DECISION` candidates from explicit user decisions
- completed task handling stores candidates in `PendingLongTermMemoryService`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryExtractorTest,TaskApplicationServiceTest test`

Expected: FAIL because no extraction or pending-candidate write-back exists.

**Step 3: Write minimal implementation**

- Add an extractor service with narrow first-version rules
- Run extraction only after completed execution, not on every intermediate step
- Save candidates into `PendingLongTermMemoryService`
- Expose candidate summaries on the completed response only for synchronous execution; async review will use a separate API in the next task

Add concise Chinese comments around the extraction guardrails so reviewers can validate why some statements are deliberately ignored.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryExtractorTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/LongTermMemoryExtractor.java \
        src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/main/java/com/agent/editor/dto/AgentTaskResponse.java \
        src/test/java/com/agent/editor/service/LongTermMemoryExtractorTest.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: extract pending long-term memory candidates"
```

### Task 7: Add Candidate Review And Confirmation APIs

**Files:**
- Create: `src/main/java/com/agent/editor/controller/LongTermMemoryController.java`
- Create: `src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java`
- Create: `src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java`
- Create: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`
- Test: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`

**Step 1: Write the failing tests**

Add controller tests proving:

- pending candidates can be listed by `taskId`
- selected candidates can be confirmed into the repository
- selected candidates can be discarded
- confirming candidates removes them from the pending store

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: FAIL because the API and confirmation flow do not exist yet.

**Step 3: Write minimal implementation**

- Add application-service methods for:
  - reading pending candidates
  - confirming selected candidates into `LongTermMemoryRepository`
  - discarding selected or all candidates for a task
- Add a dedicated controller rather than overloading existing task or diff endpoints
- Keep the first API surface focused on task-oriented candidate review

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/LongTermMemoryController.java \
        src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java \
        src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: add long-term memory confirmation api"
```

### Task 8: Run Focused Verification And Full Regression Pass

**Files:**
- Modify: `docs/plans/2026-04-03-agent-memory.md`

**Step 1: Run focused memory tests**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn \
  -Dtest=LongTermMemoryItemTest,PendingLongTermMemoryServiceTest,MilvusLongTermMemoryRepositoryTest,LongTermMemoryRetrievalServiceTest,MemorySearchToolTest,UserProfilePromptAssemblerTest,LongTermMemoryExtractorTest,LongTermMemoryControllerTest,TaskApplicationServiceTest \
  test
```

Expected: PASS

**Step 2: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

**Step 3: Update the plan with any deviations**

If implementation differs from the written plan, append a short note at the bottom of this plan explaining the justified delta.

**Step 4: Commit**

```bash
git add docs/plans/2026-04-03-agent-memory.md
git commit -m "docs: finalize agent memory implementation plan"
```

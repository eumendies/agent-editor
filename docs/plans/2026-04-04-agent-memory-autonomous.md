# Agent Memory Autonomous Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the confirmation-based long-term memory flow with a tool-driven memory system where the main agent can search and upsert memory directly, while users can manage `USER_PROFILE` through dedicated APIs.

**Architecture:** Remove pending-candidate persistence from the task-completion path, keep deterministic profile loading, extend retrieval results with stable identifiers, add a main-agent-only `memory_upsert` tool, and introduce explicit `USER_PROFILE` management endpoints. Memory correction happens in the primary model loop after `memory_search`, not through a separate conflict-detection model.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Milvus, JUnit 5, Mockito

---

### Task 1: Remove Pending Candidate Flow From Task Responses

**Files:**
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`
- Test: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing test**

Add or update tests to prove:
- task execution no longer returns pending long-term memory candidates
- task completion no longer depends on pending-memory services

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: FAIL because the response still includes pending-memory behavior.

**Step 3: Write minimal implementation**

- Remove pending-memory candidate fields from `AgentTaskResponse`
- Remove post-task candidate extraction from `TaskApplicationService`
- Keep profile loading unchanged

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/main/java/com/agent/editor/dto/AgentTaskResponse.java \
        src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "refactor: remove pending memory response flow"
```

### Task 2: Remove Pending Memory Confirmation APIs And Store

**Files:**
- Delete: `src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java`
- Delete: `src/main/java/com/agent/editor/controller/LongTermMemoryController.java`
- Delete: `src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java`
- Delete: `src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java`
- Delete: `src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java`
- Delete: `src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java`
- Delete: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`

**Step 1: Write the failing test**

This task is mostly removal. First update any remaining compilation/tests that still reference pending-memory APIs so they fail cleanly due to old references.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: FAIL on lingering pending-memory references.

**Step 3: Write minimal implementation**

- Remove pending-memory DTOs, controller, and service
- Remove corresponding application-service methods
- Keep only confirmed-memory flows

**Step 4: Run targeted tests to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add -A src/main/java/com/agent/editor/service/PendingLongTermMemoryService.java \
          src/main/java/com/agent/editor/agent/v2/core/memory/PendingLongTermMemoryItem.java \
          src/main/java/com/agent/editor/controller/LongTermMemoryController.java \
          src/main/java/com/agent/editor/dto/ConfirmLongTermMemoryRequest.java \
          src/main/java/com/agent/editor/dto/LongTermMemoryCandidateResponse.java \
          src/main/java/com/agent/editor/dto/PendingLongTermMemoryResponse.java \
          src/test/java/com/agent/editor/service/PendingLongTermMemoryServiceTest.java \
          src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java \
          src/main/java/com/agent/editor/service/TaskApplicationService.java
git commit -m "refactor: remove pending long-term memory confirmation flow"
```

### Task 3: Extend Confirmed Memory Repository For Direct CRUD

**Files:**
- Modify: `src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java`
- Modify: `src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java`
- Modify: `src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java`
- Test: `src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java`

**Step 1: Write the failing test**

Add tests proving the repository can:
- list confirmed `USER_PROFILE`
- create a confirmed memory
- delete a memory by `memoryId`
- support application-level replacement by delete + create

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest test`

Expected: FAIL because direct CRUD methods do not exist yet.

**Step 3: Write minimal implementation**

- Add repository methods for:
  - `listUserProfiles()`
  - `save(LongTermMemoryItem memory)`
  - `deleteByMemoryId(String memoryId)`
  - optional `findByMemoryId(String memoryId)` if needed by service/tool validation
- Keep replacement semantics in the application/tool layer as `delete + insert`
- Return `memoryId` in retrieval results consistently

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java \
        src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java \
        src/test/java/com/agent/editor/repository/MilvusLongTermMemoryRepositoryTest.java
git commit -m "feat: add direct CRUD for confirmed memory"
```

### Task 4: Expand memory_search Results For Rewrite/Delete

**Files:**
- Modify: `src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java`
- Modify: `src/main/java/com/agent/editor/service/LongTermMemoryRetrievalService.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java`
- Modify: `src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`

**Step 1: Write the failing test**

Add tests proving:
- `memory_search` returns `memoryId`
- `memory_search` still returns compact memory cards

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryRetrievalServiceTest,MemorySearchToolTest test`

Expected: FAIL because current results are insufficient for replace/delete flows.

**Step 3: Write minimal implementation**

- Ensure `RetrievedLongTermMemory` includes stable target identifiers
- Keep tool output compact and tool-safe

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryRetrievalServiceTest,MemorySearchToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/model/RetrievedLongTermMemory.java \
        src/main/java/com/agent/editor/service/LongTermMemoryRetrievalService.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java \
        src/test/java/com/agent/editor/service/LongTermMemoryRetrievalServiceTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java
git commit -m "feat: return stable ids from memory search"
```

### Task 5: Add memory_upsert Tool For Main-Agent Memory Writes

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`
- Create: `src/main/java/com/agent/editor/service/LongTermMemoryMutationService.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`
- Create: `src/test/java/com/agent/editor/service/LongTermMemoryMutationServiceTest.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`

**Step 1: Write the failing test**

Add tests proving:
- `CREATE` writes `USER_PROFILE` or `DOCUMENT_DECISION`
- `REPLACE` deletes old memory and creates replacement
- `DELETE` removes the targeted memory
- invalid combinations are rejected:
  - `DOCUMENT_DECISION` without `documentId`
  - `REPLACE`/`DELETE` without `memoryId`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryMutationServiceTest,MemoryUpsertToolTest test`

Expected: FAIL because the mutation service and tool do not exist yet.

**Step 3: Write minimal implementation**

- Add mutation service with application semantics:
  - `CREATE`
  - `REPLACE` as `delete + insert`
  - `DELETE`
- Keep tool schema minimal:
  - `action`
  - `memoryType`
  - `memoryId`
  - `documentId`
  - `summary`
- Restrict registration to the main execution agent path only
- Add concise Chinese comments around validation and replace semantics

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryMutationServiceTest,MemoryUpsertToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertArguments.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java \
        src/main/java/com/agent/editor/service/LongTermMemoryMutationService.java \
        src/main/java/com/agent/editor/config/ToolConfig.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java \
        src/test/java/com/agent/editor/service/LongTermMemoryMutationServiceTest.java
git commit -m "feat: add memory upsert tool"
```

### Task 6: Add Explicit USER_PROFILE Management APIs

**Files:**
- Create: `src/main/java/com/agent/editor/controller/UserProfileMemoryController.java`
- Create: `src/main/java/com/agent/editor/dto/UserProfileMemoryRequest.java`
- Create: `src/main/java/com/agent/editor/dto/UserProfileMemoryResponse.java`
- Create: `src/test/java/com/agent/editor/controller/UserProfileMemoryControllerTest.java`
- Modify: `src/main/java/com/agent/editor/service/LongTermMemoryMutationService.java`
- Test: `src/test/java/com/agent/editor/controller/UserProfileMemoryControllerTest.java`

**Step 1: Write the failing test**

Add tests proving the API can:
- list profile memories
- create a profile memory
- update a profile memory
- delete a profile memory

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=UserProfileMemoryControllerTest test`

Expected: FAIL because these endpoints do not exist yet.

**Step 3: Write minimal implementation**

- Add a controller dedicated to `USER_PROFILE`
- Use the mutation service for create/update/delete
- Keep DTOs summary-based; do not over-structure UI semantics in the backend yet

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=UserProfileMemoryControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/UserProfileMemoryController.java \
        src/main/java/com/agent/editor/dto/UserProfileMemoryRequest.java \
        src/main/java/com/agent/editor/dto/UserProfileMemoryResponse.java \
        src/main/java/com/agent/editor/service/LongTermMemoryMutationService.java \
        src/test/java/com/agent/editor/controller/UserProfileMemoryControllerTest.java
git commit -m "feat: add user profile memory management api"
```

### Task 7: Retune Prompting So Only Main Agent Writes Memory

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: any tool allowlist code touched by `memory_upsert`
- Modify: relevant tests under `src/test/java/com/agent/editor/agent/v2/...`

**Step 1: Write the failing test**

Add tests proving:
- main execution agent can see `memory_upsert`
- non-main agents do not get `memory_upsert`
- profile guidance still loads at initialization

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest test`

Expected: FAIL because tool exposure is not yet constrained correctly.

**Step 3: Write minimal implementation**

- Restrict `memory_upsert` to the main execution agent path
- Keep `memory_search` available where historical memory is still useful
- Update tool descriptions so the model knows:
  - search first when uncertain
  - write only on durable user statements or explicit correction

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "refactor: restrict memory writes to main agent"
```

### Task 8: Run Full Verification And Remove Dead References

**Files:**
- Modify: any remaining dead references revealed by compile/test
- Test: full repository

**Step 1: Run focused compile/test sweep**

Run:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusLongTermMemoryRepositoryTest,LongTermMemoryRetrievalServiceTest,MemorySearchToolTest,LongTermMemoryMutationServiceTest,MemoryUpsertToolTest,UserProfileMemoryControllerTest,TaskApplicationServiceTest test`

Expected: PASS

**Step 2: Run full test suite**

Run:
`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS with no new failures.

**Step 3: Clean dead imports and obsolete classes**

- Remove any leftover pending-candidate references
- Remove obsolete docs/comments that still describe manual memory confirmation

**Step 4: Commit verification cleanup**

```bash
git add -A
git commit -m "test: finalize autonomous memory workflow"
```

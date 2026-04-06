# Supervisor Memory Agent Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a dedicated `memory` worker to the supervisor workflow so multi-agent runs can retrieve and inline-update `DOCUMENT_DECISION` long-term memory without exposing tools directly to the supervisor.

**Architecture:** Keep the supervisor as a pure JSON router, add a new tool-loop `memory` worker with its own context factory and structured output contract, and tighten memory-tool access so only the memory worker can perform autonomous memory writes. Reuse the existing long-term-memory repository and tool implementations, but pass richer execution metadata through the runtime so `memoryUpsert` can reject illegal autonomous writes.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Lombok, Jackson, JUnit 5, Mockito

---

### Task 1: Introduce A Dedicated Memory-Worker Tool Access Boundary

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessRole.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicy.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicy.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicyTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicyTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicyTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicyTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`

**Step 1: Write the failing tests**

Update the policy tests to prove:

- a new execution role such as `MEMORY` exists
- `MEMORY` sees `searchMemory` and `upsertMemory`
- `MAIN_WRITE` no longer sees `upsertMemory`
- `MemoryUpsertTool` rejects autonomous writes that target `USER_PROFILE`
- `MemoryUpsertTool` rejects writes when the runtime identity is not the `memory` worker

Example assertion targets:

```java
assertEquals(
        List.of(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
        policy.allowedTools(ExecutionToolAccessRole.MEMORY)
);
```

```java
assertEquals("Only the memory worker may upsert long-term memory", payload.get("errorMessage").asText());
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest,MemoryUpsertToolTest test`

Expected: FAIL because the `MEMORY` role, tool-context identity fields, and upsert guardrails do not exist yet.

**Step 3: Write minimal implementation**

- add `MEMORY` to `ExecutionToolAccessRole`
- change `MemoryToolAccessPolicy` so:
  - `MEMORY` exposes `searchMemory` and `upsertMemory`
  - `MAIN_WRITE` exposes at most `searchMemory`
  - other roles expose none
- update `ExecutionToolAccessPolicy` to compose the new role correctly
- extend `ToolContext` with enough execution identity to distinguish the `memory` worker from other tool-loop paths
- pass that identity through `ToolLoopExecutionRuntime`
- harden `MemoryUpsertTool` so autonomous tool writes can only target `DOCUMENT_DECISION` and only when the caller is the `memory` worker

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest,MemoryUpsertToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessRole.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicy.java \
        src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicy.java \
        src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java \
        src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java \
        src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicyTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicyTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java
git commit -m "refactor: add memory worker tool access boundary"
```

### Task 2: Add The Memory Worker Agent, Context Factory, And Summary Contract

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgent.java`
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentContextFactory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryWorkerSummary.java`
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentContextFactoryTest.java`

**Step 1: Write the failing tests**

Mirror the existing `ResearcherAgent` test style and prove:

- `MemoryAgent` reports `AgentType.REACT`
- tool requests from the model are converted into `ToolLoopDecision.ToolCalls`
- a valid final JSON summary is parsed into `MemoryWorkerSummary`
- the context factory prompt mentions:
  - only manage `DOCUMENT_DECISION`
  - never write `USER_PROFILE`
  - store rule-style, reusable constraints only
  - prefer replace/delete over duplicate create

Suggested summary shape:

```java
{"confirmedConstraints":["keep title hierarchy"],"deprecatedConstraints":[],"activeRisks":[],"guidanceForDownstreamWorkers":"Preserve the current outline."}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryAgentTest,MemoryAgentContextFactoryTest test`

Expected: FAIL because the memory worker classes do not exist yet.

**Step 3: Write minimal implementation**

- create `MemoryWorkerSummary` as the typed structured result
- implement `MemoryAgent` by following the existing `ResearcherAgent` / `EvidenceReviewerAgent` pattern
- implement `MemoryAgentContextFactory` by following the existing worker context-factory pattern
- keep the prompt narrow:
  - search prior `DOCUMENT_DECISION` memory when relevant
  - write only stable document constraints
  - never touch `USER_PROFILE`
  - finish with strict JSON matching `MemoryWorkerSummary`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryAgentTest,MemoryAgentContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/MemoryWorkerSummary.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/MemoryAgentContextFactoryTest.java
git commit -m "feat: add supervisor memory worker"
```

### Task 3: Wire The Memory Worker Into Supervisor Configuration And Routing

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorWorkerIds.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing tests**

Add configuration and orchestration assertions proving:

- `WorkerRegistry` now includes the `memory` worker
- the `memory` worker agent is wired with `MemoryAgentContextFactory`
- `SupervisorOrchestrator` gives the `memory` worker the memory-tool whitelist instead of document-write tools
- supervisor routing can execute a memory-worker pass and continue to later workers

Example assertion targets:

```java
assertThat(workerRegistry.all())
        .extracting(SupervisorContext.WorkerDefinition::getWorkerId)
        .contains(SupervisorWorkerIds.MEMORY);
```

```java
assertEquals(
        List.of(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
        runtime.allowedTools().get(0)
);
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SupervisorOrchestratorTest test`

Expected: FAIL because the memory worker id, bean wiring, and supervisor tool resolution do not exist yet.

**Step 3: Write minimal implementation**

- add `MEMORY` to `SupervisorWorkerIds`
- register `MemoryAgentContextFactory` and `MemoryAgent` in `SupervisorAgentConfig`
- register the `memory` worker in `WorkerRegistry` with capability `memory`
- inject the execution-level tool-access policy where needed so `SupervisorOrchestrator` can assign the correct whitelist for the `memory` worker
- keep existing researcher/writer/reviewer behavior unchanged

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorWorkerIds.java \
        src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "feat: wire memory worker into supervisor orchestration"
```

### Task 4: Verify Memory Summaries Flow Through Supervisor State And Reach Downstream Workers

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- memory-worker summaries are folded into supervisor-visible transcript memory without leaking tool execution details
- a later worker sees the normalized memory summary as part of its conversation context
- the summary format remains concise even when the memory worker executed tool calls

Example target:

```java
assertTrue(secondWorkerMemory.getMessages().stream().anyMatch(message ->
        message.getText().contains("confirmedConstraints")
));
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,SupervisorOrchestratorTest test`

Expected: FAIL because the current assertions only cover researcher/writer summaries and do not verify memory-worker summary propagation.

**Step 3: Write minimal implementation**

- keep using `summarizeWorkerResult(...)` if the existing summary mechanism is sufficient
- only change `SupervisorContextFactory` if the tests show memory summaries are too noisy or lose required fields
- ensure any formatting stays generic and does not special-case worker internals more than necessary

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "test: verify supervisor memory summaries reach downstream workers"
```

### Task 5: Run Focused Regression Verification And Clean Up Search Results

**Files:**
- Modify if needed: any files found by search during cleanup

**Step 1: Search for stale assumptions**

Run: `rg -n "MAIN_WRITE\\)|UPSERT_MEMORY|USER_PROFILE|SupervisorWorkerIds\\.|capabilities\\)|memory worker" src/main/java src/test/java`

Expected:

- `UPSERT_MEMORY` is not exposed through the old main-write-only path
- `USER_PROFILE` autonomous writes are not reintroduced through tests or prompts
- the `memory` worker wiring appears in the expected config and orchestrator sites

**Step 2: Run the focused regression suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest,MemoryUpsertToolTest,MemoryAgentTest,MemoryAgentContextFactoryTest,SupervisorContextFactoryTest,SupervisorOrchestratorTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 3: Run the broader memory/supervisor regression suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryWriteServiceTest,LongTermMemoryRetrievalServiceTest,MemorySearchToolTest,MemoryUpsertToolTest,ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,SupervisorOrchestratorTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 4: Run full project tests if the focused suites stay green**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

**Step 5: Commit**

```bash
git add .
git commit -m "test: verify supervisor memory worker integration"
```

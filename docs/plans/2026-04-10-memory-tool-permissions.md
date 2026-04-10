# Memory Tool Permissions Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow write-role agents to maintain `DOCUMENT_DECISION` long-term memory and allow review-role agents to read prior document decisions.

**Architecture:** Keep `ExecutionToolAccessPolicy` as the domain policy combiner and change the role matrix inside `MemoryToolAccessPolicy`. Prompt updates teach write and review agents when to use the newly visible memory tools while `MemoryUpsertTool` continues enforcing that autonomous writes may only target `DOCUMENT_DECISION`.

**Tech Stack:** Java 17, Spring Boot, Maven, JUnit 5.

---

### Task 1: Update Memory Tool Role Matrix

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/tool/memory/MemoryToolAccessPolicyTest.java`
- Modify: `src/main/java/com/agent/editor/agent/tool/memory/MemoryToolAccessPolicy.java`

**Step 1: Write the failing tests**

Update expectations:

- `MAIN_WRITE` returns `searchMemory`, `upsertMemory`
- `MEMORY` returns `searchMemory`, `upsertMemory`
- `REVIEW` returns `searchMemory`
- `RESEARCH` returns empty list

**Step 2: Run test to verify it fails**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest test
```

Expected: fails because `MAIN_WRITE` lacks `upsertMemory` and `REVIEW` lacks `searchMemory`.

**Step 3: Implement minimal policy change**

Change `MemoryToolAccessPolicy`:

```java
private static final List<String> READ_WRITE_TOOLS = List.of(
        MemoryToolNames.SEARCH_MEMORY,
        MemoryToolNames.UPSERT_MEMORY
);
private static final List<String> REVIEW_TOOLS = List.of(
        MemoryToolNames.SEARCH_MEMORY
);
```

Return read-write tools for `MAIN_WRITE` and `MEMORY`; return review tools for `REVIEW`; return empty for `RESEARCH`.

**Step 4: Verify**

Run the same targeted test and confirm it passes.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/tool/memory/MemoryToolAccessPolicy.java src/test/java/com/agent/editor/agent/tool/memory/MemoryToolAccessPolicyTest.java
git commit -m "refactor: expand memory tool role access"
```

---

### Task 2: Update Execution Tool Combination Tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicyTest.java`

**Step 1: Write failing expectations**

Update expected combined tools:

- `MAIN_WRITE` includes document write tools plus `searchMemory`, `upsertMemory`
- `REVIEW` includes document review tools plus `searchMemory`
- `RESEARCH` remains retrieval only

**Step 2: Run test**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionToolAccessPolicyTest test
```

Expected: should pass after Task 1; if not, inspect ordering and policy composition.

**Step 3: Commit**

```bash
git add src/test/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicyTest.java
git commit -m "test: cover expanded execution memory tools"
```

---

### Task 3: Update Orchestrator Tool Expectations

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/task/SingleAgentOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/planning/PlanningThenExecutionOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/reflexion/ReflexionOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Update failing expectations**

Adjust expected tool lists:

- ReAct / single-agent write loops include `upsertMemory`
- Planning execution write loops include `upsertMemory`
- Reflexion actor includes `upsertMemory`
- Reflexion critic includes `searchMemory`
- Supervisor writer includes `upsertMemory`
- Supervisor reviewer includes `searchMemory`

**Step 2: Run focused tests**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SingleAgentOrchestratorTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest test
```

Expected: passes with updated expectations.

**Step 3: Commit**

```bash
git add src/test/java/com/agent/editor/agent/task/SingleAgentOrchestratorTest.java src/test/java/com/agent/editor/agent/planning/PlanningThenExecutionOrchestratorTest.java src/test/java/com/agent/editor/agent/reflexion/ReflexionOrchestratorTest.java src/test/java/com/agent/editor/agent/supervisor/SupervisorOrchestratorTest.java
git commit -m "test: align orchestrator memory tool access"
```

---

### Task 4: Add Prompt Guidance For Memory Tools

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/reflexion/ReflexionCriticContextFactory.java`
- Modify related tests under matching `src/test/java/.../*ContextFactoryTest.java`

**Step 1: Write failing prompt tests**

Add or update assertions:

- write prompts mention `searchMemory`
- write prompts mention `upsertMemory`
- write prompts say autonomous writes are only for durable `DOCUMENT_DECISION`
- write prompts say not to write `USER_PROFILE`
- review prompts mention `searchMemory`
- review prompts do not mention `upsertMemory`

**Step 2: Run prompt tests and verify failure**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,ReflexionCriticContextFactoryTest test
```

Expected: fails on missing prompt guidance.

**Step 3: Implement prompt text**

Add concise `## Memory Rules` sections:

For write roles:

```text
Use searchMemory when prior document decisions may affect this task.
Use upsertMemory only for durable DOCUMENT_DECISION memory: stable document constraints, confirmed tradeoffs, or reusable decisions that should affect future edits.
Do not write USER_PROFILE memory, execution logs, one-off edits, or temporary plans.
Prefer replace/delete over duplicate create when an older memory is stale.
```

For review roles:

```text
Use searchMemory to check prior document decisions when they may constrain the verdict.
Treat retrieved document decisions as review constraints.
Do not write memory from review.
```

**Step 4: Verify prompt tests**

Run the same targeted tests and confirm they pass.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/react/ReactAgentContextFactory.java src/main/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactory.java src/main/java/com/agent/editor/agent/supervisor/worker/EvidenceReviewerAgentContextFactory.java src/main/java/com/agent/editor/agent/reflexion/ReflexionCriticContextFactory.java src/test/java/com/agent/editor/agent/react/ReactAgentContextFactoryTest.java src/test/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactoryTest.java src/test/java/com/agent/editor/agent/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java src/test/java/com/agent/editor/agent/reflexion/ReflexionCriticContextFactoryTest.java
git commit -m "refactor: guide agents on memory tool usage"
```

---

### Task 5: Final Verification

**Files:**
- No direct edits expected.

**Step 1: Search for stale expectations**

Run:

```bash
rg -n "SEARCH_MEMORY|UPSERT_MEMORY|searchMemory|upsertMemory" src/test/java src/main/java
```

Review any old assertions that still imply `MAIN_WRITE` is read-only or `REVIEW` has no memory tools.

**Step 2: Run full test suite**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: all tests pass, with the known skipped bad-case test if present.

**Step 3: Commit any remaining cleanup**

If the final search required changes:

```bash
git add <changed-files>
git commit -m "test: remove stale memory permission expectations"
```

Otherwise do not create an empty commit.

# Agent V2 Execution Checkpoint Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `agent.v2` 引入可恢复的 `ExecutionState` checkpoint 机制，让 Plan-and-Execute 和后续 human-in-the-loop 场景都能在 step 间复用执行上下文，而不是只传递文档内容。

**Architecture:** 保留现有 runtime/orchestrator 分层，去掉 `ExecutionState.toolResults`，引入 `ExecutionStage` 与本地 transcript 型 `ExecutionMemory`，让 `DefaultExecutionRuntime` 接收初始 `ExecutionState` 并返回带 `finalState` 的 `ExecutionResult`。`PlanningThenExecutionOrchestrator` 负责跨 step 复用 state，`ReactAgentDefinition` 负责把 transcript memory 转成 LangChain4j messages。

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, langchain4j, existing agent.v2 runtime/orchestrator structure

---

### Task 1: Introduce ExecutionStage And Transcript-Oriented ExecutionState

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/state/ExecutionState.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/ExecutionStage.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/ExecutionMemory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/ChatTranscriptMemory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/ExecutionMessage.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequestTest.java`

**Step 1: Write the failing test**

Add tests proving:

- `ExecutionState` no longer exposes `completed` or `toolResults`
- `ExecutionState` requires `currentContent`, `memory`, and `stage`
- `ExecutionStage` contains `RUNNING`, `COMPLETED`, `WAITING_FOR_HUMAN`, and `FAILED`

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ExecutionRequestTest test`

Expected: FAIL because the new execution state model does not exist yet.

**Step 3: Write minimal implementation**

Implement:

- `ExecutionStage`
- `ExecutionMemory`
- transcript-oriented `ExecutionState`
- a minimal `ChatTranscriptMemory`
- a minimal `ExecutionMessage` model that does not depend on LangChain4j classes

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ExecutionRequestTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/state/ExecutionState.java src/main/java/com/agent/editor/agent/v2/core/state/ExecutionStage.java src/main/java/com/agent/editor/agent/v2/core/state/ExecutionMemory.java src/main/java/com/agent/editor/agent/v2/core/state/ChatTranscriptMemory.java src/main/java/com/agent/editor/agent/v2/core/state/ExecutionMessage.java src/test/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequestTest.java
git commit -m "refactor: introduce execution checkpoint state model"
```

### Task 2: Extend Runtime API To Accept And Return ExecutionState

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionResult.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Add tests proving:

- runtime can start from an externally provided `ExecutionState`
- runtime still supports the old convenience entrypoint
- `ExecutionResult` returns `finalState`

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=DefaultExecutionRuntimeTest test`

Expected: FAIL because runtime does not accept initial state or return final state yet.

**Step 3: Write minimal implementation**

Implement:

- overloaded `run(..., ExecutionState initialState)`
- old `run(...)` delegating to the new overload
- `ExecutionResult.finalState`
- runtime loop termination driven by `ExecutionStage` instead of `completed`

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=DefaultExecutionRuntimeTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRuntime.java src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionResult.java src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java
git commit -m "refactor: make runtime checkpoint-aware"
```

### Task 3: Move Tool Execution History Into ExecutionMemory

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Add tests proving:

- tool execution results are appended into transcript memory
- `ReactAgentDefinition` reads transcript memory instead of `toolResults`
- React still maps model tool calls and completions correctly

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=DefaultExecutionRuntimeTest,ReactAgentDefinitionTest test`

Expected: FAIL because runtime still stores tool history in `toolResults` and React still reads that field.

**Step 3: Write minimal implementation**

Implement:

- runtime-side transcript append for model-visible history
- `ExecutionContext` accessors for the new memory model
- React-side conversion from transcript memory to LangChain4j messages
- removal of `toolResults` access from React

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=DefaultExecutionRuntimeTest,ReactAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionContext.java src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java
git commit -m "refactor: move execution history into transcript memory"
```

### Task 4: Reuse ExecutionState Across Planning Steps

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving:

- the second plan step receives the first step's transcript memory
- step boundary instructions are appended before each subtask run
- planning execution still returns the latest `finalContent`

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PlanningThenExecutionOrchestratorTest test`

Expected: FAIL because planning currently only forwards document content.

**Step 3: Write minimal implementation**

Implement:

- creation of an initial shared `ExecutionState`
- per-step reuse of `ExecutionResult.finalState`
- explicit insertion of a step boundary user message before each runtime call

Keep `PlanningAgentDefinition` unchanged unless a test proves it needs transcript awareness.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PlanningThenExecutionOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java
git commit -m "feat: share execution checkpoint across planning steps"
```

### Task 5: Keep Supervisor Worker State Isolation Explicit

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add tests proving:

- workers do not accidentally share transcript memory by default
- supervisor orchestration still receives the worker's latest `finalContent`
- future shared-state behavior can be added without changing runtime semantics

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SupervisorOrchestratorTest,HybridSupervisorAgentDefinitionTest test`

Expected: FAIL because supervisor still assumes stateless worker invocations.

**Step 3: Write minimal implementation**

Implement:

- explicit fresh `ExecutionState` creation per worker dispatch
- clear comments and helper methods documenting why worker transcript memory is isolated by default

Do not introduce shared worker memory in this task.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SupervisorOrchestratorTest,HybridSupervisorAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "refactor: make supervisor worker checkpoint scope explicit"
```

### Task 6: Verify Resume-Oriented Runtime Behavior

**Files:**
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Run focused regression tests**

Run: `mvn -Dtest=ExecutionRequestTest,DefaultExecutionRuntimeTest,ReactAgentDefinitionTest,PlanningThenExecutionOrchestratorTest,SupervisorOrchestratorTest,HybridSupervisorAgentDefinitionTest test`

Expected: PASS

**Step 2: Run the full suite**

Run: `mvn test`

Expected: PASS

**Step 3: Commit**

```bash
git add docs/plans/2026-03-16-agent-v2-execution-checkpoint-design.md docs/plans/2026-03-16-agent-v2-execution-checkpoint-implementation.md
git commit -m "docs: plan execution checkpoint refactor"
```

# Hybrid Supervisor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `SUPERVISOR` 模式新增一个混合式多轮调度 supervisor，实现基于能力标签的候选筛选、受限 LLM 选 worker、允许重复调度和可验证的防循环收口。

**Architecture:** 保留 `SupervisorAgentDefinition` 接口和 `SupervisorOrchestrator` 主体循环。新增 `HybridSupervisorAgentDefinition` 负责规则筛候选、构造 supervisor prompt、解析结构化决策并在非法输出时兜底；同时给 `WorkerDefinition` 增加最小 `capabilities` 元数据，并把 Spring 默认装配切到新实现。

**Tech Stack:** Java 17, Spring Boot, LangChain4j `ChatModel`, JUnit 5, existing trace infrastructure

---

### Task 1: Add Worker Capability Metadata

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/WorkerDefinition.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Extend `AgentV2ConfigurationSplitTest` to assert that:

- the `WorkerRegistry` still loads
- each registered worker exposes non-empty `capabilities`
- `analyzer`, `editor`, and `reviewer` map to the expected capability tags

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigurationSplitTest test
```

Expected: FAIL because `WorkerDefinition` does not expose capability metadata yet.

**Step 3: Write minimal implementation**

Add `capabilities` to `WorkerDefinition` and update `SupervisorAgentConfig` registrations:

- `analyzer` -> `List.of("analyze")`
- `editor` -> `List.of("edit")`
- `reviewer` -> `List.of("review")`

Keep existing role, description, agentDefinition, and allowedTools unchanged.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/WorkerDefinition.java src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git commit -m "feat: add supervisor worker capabilities"
```

### Task 2: Add the First Failing Tests for Hybrid Supervisor Decisions

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add focused tests covering:

- `shouldPreferAnalyzerOnFirstPassWhenInstructionNeedsInspection`
- `shouldReturnAssignedWorkerSelectedByModelWithinCandidates`
- `shouldFallbackToRuleBasedWorkerWhenModelReturnsUnknownWorker`
- `shouldCompleteWhenModelRequestsCompletion`

Use a stub `ChatModel` that returns controlled JSON strings so tests exercise real parsing and rule fallback behavior.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=HybridSupervisorAgentDefinitionTest test
```

Expected: FAIL because `HybridSupervisorAgentDefinition` does not exist yet.

**Step 3: Write minimal implementation**

Do not write implementation in this task. Stop after the failing test is in place.

**Step 4: Run test to verify it still fails for the right reason**

Run the same command and confirm the failure is due to missing production classes, not a broken test.

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "test: define hybrid supervisor decision behavior"
```

### Task 3: Implement Minimal Hybrid Supervisor Decision Logic

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing test**

Use the test class from Task 2 as the red test.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=HybridSupervisorAgentDefinitionTest test
```

Expected: FAIL because the implementation is missing.

**Step 3: Write minimal implementation**

Implement `HybridSupervisorAgentDefinition` with:

- constructor injection for `ChatModel`
- rule-based candidate filtering from `instruction`, `availableWorkers`, and `workerResults`
- a structured supervisor prompt
- JSON parsing into an internal decision object
- conversion to existing `SupervisorDecision`
- one-step rule fallback when the model output is invalid

Keep the first version narrow. Only support:

- `assign_worker`
- `complete`

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "feat: add hybrid supervisor decision logic"
```

### Task 4: Add Loop Guard Tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add tests for:

- `shouldCompleteWhenNoCandidateWorkersRemain`
- `shouldStopAfterRepeatedNoProgress`
- `shouldDemoteSameWorkerAfterConsecutiveSelections`

Model outputs should be set up to try repeating the same worker or continue despite unchanged content.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=HybridSupervisorAgentDefinitionTest test
```

Expected: FAIL because loop guards are not implemented yet.

**Step 3: Write minimal implementation**

Extend `HybridSupervisorAgentDefinition` to add:

- maximum consecutive invalid output handling
- repeated worker demotion or termination
- no-progress completion when recent worker outputs and content do not change

Prefer simple counters derived from `workerResults` over new persisted state objects.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "feat: add supervisor loop guards"
```

### Task 5: Integrate the New Supervisor Bean

**Files:**
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Extend `AgentV2ConfigurationSplitTest` to assert:

- the context has a `SupervisorAgentDefinition` bean
- the bean type is `HybridSupervisorAgentDefinition`
- `TaskOrchestrator` still wires successfully

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigurationSplitTest test
```

Expected: FAIL because configuration still wires `SequentialSupervisorAgentDefinition`.

**Step 3: Write minimal implementation**

Change configuration so:

- `SupervisorAgentConfig` exposes `HybridSupervisorAgentDefinition`
- `TaskOrchestratorConfig` depends on `SupervisorAgentDefinition` rather than the sequential concrete type

Keep `SequentialSupervisorAgentDefinition` available only if still useful for tests or reference.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git commit -m "feat: wire hybrid supervisor by default"
```

### Task 6: Verify Orchestrator Integration With Dynamic Multi-Turn Routing

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing test**

Add or revise an orchestrator-level test proving:

- the supervisor can assign the same worker more than once
- worker outputs are fed back into later supervisor decisions
- the orchestration still completes within the dispatch budget

Use a scripted supervisor stub or the real `HybridSupervisorAgentDefinition` with a stub `ChatModel`, whichever keeps the test focused on orchestrator integration rather than prompt parsing.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=SupervisorOrchestratorTest test
```

Expected: FAIL because the current assertions only cover one-pass heterogenous worker flow.

**Step 3: Write minimal implementation**

Update tests and only the minimum production code needed for integration correctness. If the existing orchestrator already passes once the new supervisor is wired, do not change orchestrator behavior unnecessarily.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java src/main/java/com/agent/editor/agent/v2/supervisor
git commit -m "test: verify hybrid supervisor orchestration flow"
```

### Task 7: Run Focused Regression Tests

**Files:**
- No production file changes expected

**Step 1: Run the focused supervisor and configuration suite**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=HybridSupervisorAgentDefinitionTest,SupervisorOrchestratorTest,AgentV2ConfigurationSplitTest test
```

Expected: PASS.

**Step 2: Run the full suite**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test
```

Expected: PASS with no supervisor regressions.

**Step 3: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java src/main/java/com/agent/editor/agent/v2/supervisor/WorkerDefinition.java src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git commit -m "test: verify hybrid supervisor feature"
```

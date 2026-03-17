# Agent V2 Reflexion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `agent.v2` 新增独立顶层的 `Reflexion Agent`，实现固定的 `actor -> critic -> pass/revise -> actor retry` 自反馈循环，并通过 critic verdict 控制结束。

**Architecture:** 新增 `AgentType.REFLEXION`、`ReflexionOrchestrator`、`ReflexionActorDefinition`、`ReflexionCriticDefinition` 以及结构化 `ReflexionCritique` 模型。actor 复用 `ExecutionRuntime` 并跨轮保持 `ExecutionState`，critic 每轮 fresh 执行，只允许分析类工具，不允许编辑文档。

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, langchain4j, existing agent.v2 runtime/orchestrator structure

---

### Task 1: Add Reflexion Core Types

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/agent/AgentType.java`
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionVerdict.java`
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritique.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/agent/DecisionTest.java`

**Step 1: Write the failing test**

Add tests proving:

- `AgentType` contains `REFLEXION`
- `ReflexionVerdict` supports `PASS` and `REVISE`
- `ReflexionCritique` preserves verdict and feedback fields

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DecisionTest test`

Expected: FAIL because reflexion core types do not exist yet.

**Step 3: Write minimal implementation**

Implement:

- `AgentType.REFLEXION`
- `ReflexionVerdict`
- `ReflexionCritique`

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/agent/AgentType.java src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionVerdict.java src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritique.java src/test/java/com/agent/editor/agent/v2/core/agent/DecisionTest.java
git commit -m "feat: add reflexion core types"
```

### Task 2: Add Critic Definition And Structured Verdict Mapping

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticAiService.java`
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`

**Step 1: Write the failing test**

Add tests proving:

- critic maps `PASS` responses correctly
- critic maps `REVISE` responses correctly
- invalid or null critic responses fail explicitly

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReflexionCriticDefinitionTest test`

Expected: FAIL because critic definition does not exist yet.

**Step 3: Write minimal implementation**

Implement:

- critic AiService prompt contract
- `ReflexionCriticDefinition`
- explicit validation of structured verdict output

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticAiService.java src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinition.java src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java
git commit -m "feat: add reflexion critic definition"
```

### Task 3: Add Reflexion Orchestrator Pass/Revise Loop

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving:

- actor output followed by critic `PASS` returns immediately
- critic `REVISE` feeds critique back into the next actor round
- actor state is reused across rounds
- critic state is fresh per round
- max reflection rounds stops the loop and returns latest actor content

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReflexionOrchestratorTest test`

Expected: FAIL because reflexion orchestration does not exist yet.

**Step 3: Write minimal implementation**

Implement:

- actor execution via `ExecutionRuntime`
- critic execution per round
- critique feedback appended into actor memory
- pass/revise stop logic
- max round limit

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java
git commit -m "feat: add reflexion orchestration loop"
```

### Task 4: Add Reflexion Actor Wiring And Tool Boundaries

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorDefinition.java`
- Create: `src/main/java/com/agent/editor/config/ReflexionAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/task/RoutingTaskOrchestrator.java` if needed by config wiring
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving:

- `AgentType.REFLEXION` is wired in routing
- actor and critic are configured independently
- critic only receives analysis-class allowed tools
- actor receives editing-class allowed tools

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigurationSplitTest,ReflexionOrchestratorTest test`

Expected: FAIL because reflexion config and tool boundaries are not wired yet.

**Step 3: Write minimal implementation**

Implement:

- reflexion actor definition
- dedicated reflexion config
- actor tool allow-list: `editDocument`, `searchContent`
- critic tool allow-list: `searchContent`, `analyzeDocument`

Do not reuse `SupervisorAgentConfig` reviewer worker.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorDefinition.java src/main/java/com/agent/editor/config/ReflexionAgentConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java
git commit -m "feat: wire reflexion agent configuration"
```

### Task 5: Add Trace Coverage For Reflexion Stages

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving trace records are emitted for:

- actor started/completed
- critic started/completed
- revise
- pass
- max rounds reached

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReflexionOrchestratorTest test`

Expected: FAIL because reflexion trace stages are not recorded yet.

**Step 3: Write minimal implementation**

Implement trace emission at each reflexion orchestration boundary.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java
git commit -m "feat: add reflexion trace stages"
```

### Task 6: Regression Verification

**Files:**
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Run focused regression tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReflexionCriticDefinitionTest,ReflexionOrchestratorTest,DefaultExecutionRuntimeTest,ReactAgentDefinitionTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 2: Run the full suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test`

Expected: PASS

**Step 3: Commit**

```bash
git add docs/plans/2026-03-17-agent-v2-reflexion-design.md docs/plans/2026-03-17-agent-v2-reflexion-implementation.md
git commit -m "docs: plan reflexion agent implementation"
```

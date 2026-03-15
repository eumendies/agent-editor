# Agent V2 Package Restructure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 重组 `agent.v2` 的目录和包结构，让内核、模式、任务入口和横切子系统的边界更加清晰，同时保持现有行为不变。

**Architecture:** 采用“模式优先 + 内核独立”的包结构。先迁移不带行为变化的纯模型，再迁移核心接口与 runtime，然后迁移模式专属实现，最后拆分配置装配文件。整个过程不改 API、不改业务逻辑，只做包边界整理和 import/config 修复。

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Maven, package refactor only

---

### Task 1: Move Core Model Types

**Files:**
- Move: `src/main/java/com/agent/editor/agent/v2/definition/AgentType.java`
- Move: `src/main/java/com/agent/editor/agent/v2/definition/Decision.java`
- Move: `src/main/java/com/agent/editor/agent/v2/definition/ToolCall.java`
- Move: `src/main/java/com/agent/editor/agent/v2/state/DocumentSnapshot.java`
- Move: `src/main/java/com/agent/editor/agent/v2/state/ExecutionState.java`
- Move: `src/main/java/com/agent/editor/agent/v2/state/TaskState.java`
- Move: `src/main/java/com/agent/editor/agent/v2/state/TaskStatus.java`
- Modify imports in affected main/test files
- Test: `src/test/java/com/agent/editor/agent/v2/core/DecisionTest.java`

**Step 1: Write the failing test**

Move or create a minimal `DecisionTest` under the new `core` path so package resolution fails until imports are updated.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DecisionTest test
```

Expected: FAIL due to moved packages not yet wired.

**Step 3: Write minimal implementation**

Move the files into:

- `agent/v2/core/agent`
- `agent/v2/core/state`

Update imports only. Do not modify behavior.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core src/test/java/com/agent/editor/agent/v2/core
git commit -m "refactor: move agent v2 core model types"
```

### Task 2: Move Core Runtime Types

**Files:**
- Move: `src/main/java/com/agent/editor/agent/v2/definition/AgentDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntime.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionContext.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionRequest.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionResult.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionRuntime.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionStateSnapshot.java`
- Move: `src/main/java/com/agent/editor/agent/v2/runtime/TerminationPolicy.java`
- Modify imports in affected main/test files
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Move the runtime test into the new package path or add a compilation-based test adjustment so the package change breaks until code is updated.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DefaultExecutionRuntimeTest test
```

Expected: FAIL due to package mismatch/import failures.

**Step 3: Write minimal implementation**

Move runtime files into:

- `agent/v2/core/agent`
- `agent/v2/core/runtime`

Update imports only.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core src/test/java/com/agent/editor/agent/v2/core/runtime
git commit -m "refactor: move agent v2 core runtime types"
```

### Task 3: Move Planning Package

**Files:**
- Move: `src/main/java/com/agent/editor/agent/v2/definition/PlanningAgentDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanResult.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanStep.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestrator.java`
- Modify imports in affected main/test files
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Write the failing test**

Move planning tests into the target package path so imports fail until the production packages are updated.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=PlanningAgentDefinitionTest,PlanningThenExecutionOrchestratorTest test
```

Expected: FAIL due to package/import mismatch.

**Step 3: Write minimal implementation**

Move planning files into `agent/v2/planning` and fix imports only.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/planning src/test/java/com/agent/editor/agent/v2/planning
git commit -m "refactor: move agent v2 planning package"
```

### Task 4: Move Supervisor Package

**Files:**
- Move: `src/main/java/com/agent/editor/agent/v2/definition/SequentialSupervisorAgentDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/definition/SupervisorAgentDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/SupervisorContext.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/SupervisorDecision.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/SupervisorOrchestrator.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/WorkerDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/WorkerRegistry.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/WorkerResult.java`
- Modify imports in affected main/test files
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing test**

Move supervisor tests to the target package path so the package migration breaks until production code is updated.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=SupervisorOrchestratorTest test
```

Expected: FAIL due to unresolved packages/imports.

**Step 3: Write minimal implementation**

Move supervisor files into `agent/v2/supervisor` and fix imports only.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor src/test/java/com/agent/editor/agent/v2/supervisor
git commit -m "refactor: move agent v2 supervisor package"
```

### Task 5: Move React And Task Packages

**Files:**
- Move: `src/main/java/com/agent/editor/agent/v2/definition/ReactAgentDefinition.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskOrchestrator.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskRequest.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskResult.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/SingleAgentOrchestrator.java`
- Move: `src/main/java/com/agent/editor/agent/v2/orchestration/RoutingTaskOrchestrator.java`
- Modify imports in affected main/test files
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/task/RoutingTaskOrchestratorTest.java`

**Step 1: Write the failing test**

Move tests into target package paths and verify import resolution fails first.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReactAgentDefinitionTest,SingleAgentOrchestratorTest,RoutingTaskOrchestratorTest test
```

Expected: FAIL because package moves are incomplete.

**Step 3: Write minimal implementation**

Move:

- `ReactAgentDefinition` to `agent/v2/react`
- generic task files to `agent/v2/task`

Update imports only.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react src/main/java/com/agent/editor/agent/v2/task src/test/java/com/agent/editor/agent/v2/react src/test/java/com/agent/editor/agent/v2/task
git commit -m "refactor: separate react and task packages"
```

### Task 6: Split AgentV2Config

**Files:**
- Create: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Create: `src/main/java/com/agent/editor/config/TraceConfig.java`
- Create: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Create: `src/main/java/com/agent/editor/config/PlanningAgentConfig.java`
- Create: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Create: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Delete or deprecate: `src/main/java/com/agent/editor/config/AgentV2Config.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigTest.java` or replacement config tests

**Step 1: Write the failing test**

Add a configuration wiring test that asserts beans still resolve after config splitting.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigTest test
```

Expected: FAIL because the old config layout no longer matches the test.

**Step 3: Write minimal implementation**

Split `AgentV2Config` by concern:

- tools
- trace
- react
- planning
- supervisor
- routing/orchestrator assembly

Do not change bean semantics while splitting.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config
git commit -m "refactor: split agent v2 configuration by concern"
```

### Task 7: Align Remaining Tests And Imports

**Files:**
- Modify all remaining `src/test/java/com/agent/editor/agent/v2/**` imports and package declarations
- Modify any remaining production imports across `service`, `controller`, `config`, `event`, `trace`, `tool`

**Step 1: Write the failing test**

No new test required; use existing compilation and focused tests as the failing signal.

**Step 2: Run targeted verification to surface unresolved references**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReactAgentDefinitionTest,PlanningThenExecutionOrchestratorTest,SupervisorOrchestratorTest,TraceControllerTest test
```

Expected: FAIL if any stale imports or package declarations remain.

**Step 3: Write minimal implementation**

Fix remaining imports and move any straggler tests into the new package layout.

**Step 4: Run the targeted verification again**

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "refactor: align agent v2 imports with new package layout"
```

### Task 8: Full Verification

**Files:**
- No code changes required unless verification finds issues

**Step 1: Run full test suite**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test
```

Expected: PASS.

**Step 2: Smoke-check runtime startup**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn spring-boot:run
```

Expected: application starts successfully and still serves the demo page.

**Step 3: Commit any final package-fix adjustments if needed**

If verification required changes:

```bash
git add <changed-files>
git commit -m "fix: finalize agent v2 package restructure"
```

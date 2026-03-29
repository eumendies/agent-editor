# Supervisor Runtime Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a dedicated `SupervisorExecutionRuntime` so supervisor agents are executed through runtime infrastructure instead of being called directly from `SupervisorOrchestrator`.

**Architecture:** Implement a minimal single-decision runtime aligned with `PlanningExecutionRuntime`, then route `SupervisorOrchestrator` through it while keeping worker dispatch orchestration in the orchestrator. This keeps agent execution and orchestration responsibilities separated.

**Tech Stack:** Java 17, Spring Boot, Lombok, JUnit 5, Mockito

---

### Task 1: Add Supervisor Runtime Red Test

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntimeTest.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntime.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/core/agent/SupervisorAgent.java`

**Step 1: Write the failing test**

Cover:
- rejecting non-`SupervisorAgent`
- rejecting non-`SupervisorContext` initial state
- returning `ExecutionResult<SupervisorDecision.AssignWorker>`
- returning `ExecutionResult<SupervisorDecision.Complete>`
- publishing `TASK_STARTED` and `TASK_COMPLETED`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorExecutionRuntimeTest test`

Expected: FAIL because `SupervisorExecutionRuntime` does not exist yet.

**Step 3: Write minimal implementation**

Create `SupervisorExecutionRuntime` with only the behavior required by the tests.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorExecutionRuntimeTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java src/test/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntimeTest.java
git commit -m "feat: add supervisor execution runtime"
```

### Task 2: Route SupervisorOrchestrator Through Runtime

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`

**Step 1: Write the failing test**

Update `SupervisorOrchestratorTest` so it asserts:
- supervisor decisions are produced through `SupervisorExecutionRuntime`
- orchestrator still preserves current worker dispatch behavior

Use a recording runtime or stub to prove the orchestrator no longer directly owns the `decide(...)` call boundary.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest test`

Expected: FAIL because orchestrator still directly invokes the agent.

**Step 3: Write minimal implementation**

Inject `SupervisorExecutionRuntime` into `SupervisorOrchestrator`, build a supervisor `ExecutionRequest`, call the runtime, and branch on `supervisorResult.getResult()`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorExecutionRuntimeTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java src/test/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntimeTest.java
git commit -m "refactor: route supervisor orchestrator through runtime"
```

### Task 3: Wire Supervisor Runtime Through Spring Configuration

**Files:**
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Update configuration test to assert:
- `SupervisorExecutionRuntime` bean exists
- `SupervisorOrchestrator` is wired with it

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest test`

Expected: FAIL because the runtime bean is not registered yet.

**Step 3: Write minimal implementation**

Register `SupervisorExecutionRuntime` in config and inject it into `SupervisorOrchestrator`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SupervisorExecutionRuntimeTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java
git commit -m "refactor: wire supervisor runtime through configuration"
```

### Task 4: Run Supervisor Runtime Regression Suite

**Files:**
- Modify: none unless regressions appear
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`

**Step 1: Run targeted regression suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorExecutionRuntimeTest,SupervisorOrchestratorTest,AgentV2ConfigurationSplitTest,HybridSupervisorAgentTest,SupervisorContextFactoryTest test
```

Expected: PASS

**Step 2: Fix any supervisor-path regressions**

Only touch the supervisor runtime, orchestrator, config, or directly related tests unless a concrete break requires more.

**Step 3: Re-run the regression suite**

Run the same command again until green.

**Step 4: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/runtime src/main/java/com/agent/editor/agent/v2/supervisor src/main/java/com/agent/editor/config src/test/java/com/agent/editor/agent/v2/core/runtime src/test/java/com/agent/editor/agent/v2/supervisor src/test/java/com/agent/editor/config
git commit -m "refactor: execute supervisor agent through runtime"
```

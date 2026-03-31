# Researcher Iteration Cap Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce the worker loop budget for `ResearcherAgent` so supervisor-dispatched research runs cap at four iterations instead of inheriting a larger task-wide budget.

**Architecture:** Keep the policy in `SupervisorOrchestrator` by deriving a worker-specific `maxIterations` before building each worker `ExecutionRequest`. Researcher gets `min(taskMaxIterations, 4)`; other workers keep the original task budget unchanged.

**Tech Stack:** Java 17, Spring Boot, JUnit 5

---

### Task 1: Lock Down Researcher-Specific Iteration Capping

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing test**

Add a test that schedules a `researcher` worker first and a non-researcher worker second, then asserts:

- the researcher worker request uses `maxIterations == 4`
- the later non-researcher request still uses the original task `maxIterations`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest#shouldCapResearcherWorkerIterationsAtFour test`

Expected: FAIL because `SupervisorOrchestrator` still forwards the task budget unchanged.

**Step 3: Write minimal implementation**

Modify `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java` to:

- add a helper resolving worker-specific max iterations
- clamp researcher to `Math.min(request.getMaxIterations(), 4)`
- use that value when constructing worker `ExecutionRequest`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest#shouldCapResearcherWorkerIterationsAtFour test`

Expected: PASS

**Step 5: Commit**

```bash
git add docs/plans/2026-03-31-researcher-iteration-cap-design.md \
        docs/plans/2026-03-31-researcher-iteration-cap.md \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "refactor: cap researcher worker iterations"
```

### Task 2: Run Focused Orchestrator Regression

**Files:**
- Verify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Run focused regression**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest test`

Expected: PASS

**Step 2: Commit if additional adjustments were needed**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "test: cover researcher iteration cap"
```

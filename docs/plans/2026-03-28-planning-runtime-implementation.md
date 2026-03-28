# Planning Runtime Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement `PlanningExecutionRuntime` as the single-pass planning runtime and repair planning tests broken by the refactor.

**Architecture:** Keep planning as a runtime concern only for plan creation, state shaping, and event publication. Leave plan-step execution inside `PlanningThenExecutionOrchestrator`, and adapt tests to the new `PlanningAgent.createPlan(AgentRunContext)` and `core.agent.PlanResult` model.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Maven

---

### Task 1: Add planning runtime tests

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Add tests for completed planning flow, resumed state, preserved memory, and wrong agent type.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningExecutionRuntimeTest test`
Expected: FAIL because `PlanningExecutionRuntime.runInternal` is not implemented.

**Step 3: Write minimal implementation**

Implement runtime behavior for single-pass planning with transcript updates and event publication.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningExecutionRuntimeTest test`
Expected: PASS

### Task 2: Repair planning orchestration tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Write the failing test changes**

Update the test doubles to current planning agent and plan result APIs.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningThenExecutionOrchestratorTest test`
Expected: FAIL or compile error until the new planner/test doubles match the refactor.

**Step 3: Write minimal implementation**

Adjust test fixtures and assertions without expanding production scope.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningThenExecutionOrchestratorTest test`
Expected: PASS

### Task 3: Run combined verification

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntime.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Run targeted tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningExecutionRuntimeTest,PlanningThenExecutionOrchestratorTest test`
Expected: PASS

**Step 2: Run broader runtime/planning checks if needed**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningAgentImplTest,PlanningExecutionRuntimeTest,PlanningThenExecutionOrchestratorTest test`
Expected: PASS

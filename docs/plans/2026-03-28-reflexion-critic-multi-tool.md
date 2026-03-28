# Reflexion Critic Multi-Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Update `ReflexionCritic` so it can perform real multi-round tool calling before producing the final structured `ReflexionCritique`.

**Architecture:** Keep `ReflexionCritic` as a single `ToolLoopAgent` and let `ToolLoopExecutionRuntime` continue driving the loop. The critic switches between an analysis request shape that allows tool calls and a finalization request shape that forces strict JSON critique output after tool results are present in memory.

**Tech Stack:** Java 17, LangChain4j, Spring Boot, JUnit 5, Maven

---

### Task 1: Add critic red tests for multi-tool flow

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`

**Step 1: Write the failing test**

Add a test where the first critic response is a structured tool call and the second response, after a tool result is present in memory, is strict critique JSON.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest test`
Expected: FAIL because `ReflexionCritic` currently always requests strict JSON and does not distinguish analysis/finalization phases.

**Step 3: Write minimal implementation**

Teach `ReflexionCritic` to detect tool-result memory and build different request contracts for analysis vs finalization.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest test`
Expected: PASS

### Task 2: Add orchestrator regression coverage

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing test**

Add a test that covers a critic requiring tool calls before it can produce a `PASS` or `REVISE` verdict.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionOrchestratorTest test`
Expected: FAIL until the critic contract supports multi-round tool behavior.

**Step 3: Write minimal implementation**

Use the updated critic behavior without changing orchestrator architecture.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionOrchestratorTest test`
Expected: PASS

### Task 3: Verify neighboring runtime behavior

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritic.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Run targeted verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest,ReflexionOrchestratorTest,ToolLoopExecutionRuntimeTest test`
Expected: PASS

**Step 2: Run broader reflexion/runtime verification if needed**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest,ReflexionOrchestratorTest,ToolLoopExecutionRuntimeTest,PlanningExecutionRuntimeTest test`
Expected: PASS

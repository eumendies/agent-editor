# Agent Context Factory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce `AgentContextFactory` and `ModelInvocationContext` so runtime-state assembly and model-message construction are centralized by agent paradigm.

**Architecture:** Keep `AgentRunContext` as runtime state, add a separate `ModelInvocationContext` as the exact model-facing payload, and route React/Planning/Reflexion paradigms through explicit `AgentContextFactory` implementations. Runtimes stop injecting instruction text, orchestrators stop hand-building transcript state, and agent classes stop owning prompt/message assembly.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, JUnit 5, Maven

---

### Task 1: Add factory-layer contracts and red tests

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/core/context/AgentContextFactory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/context/ModelInvocationContext.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Add tests proving runtimes do not implicitly inject `request.instruction` into memory and that model-facing request construction is no longer runtime-owned.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningExecutionRuntimeTest test`
Expected: FAIL because runtimes still perform hidden instruction ingestion and there is no factory-layer contract yet.

**Step 3: Write minimal implementation**

Add the new contracts and remove runtime-level implicit transcript injection.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningExecutionRuntimeTest test`
Expected: PASS

### Task 2: Migrate Reflexion critic to the new abstraction

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritic.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`

**Step 1: Write the failing test**

Add or refine tests so `ReflexionCritic` no longer owns direct message-building logic and instead depends on factory-produced `ModelInvocationContext`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest test`
Expected: FAIL until the critic uses the new factory contract.

**Step 3: Write minimal implementation**

Move critic prompt/message construction, tools, and response-format assembly into `ReflexionCriticContextFactory`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest test`
Expected: PASS

### Task 3: Migrate React paradigm

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests that prove React entry context and model messages come from the factory rather than runtime/orchestrator-side hidden behavior.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,SingleAgentOrchestratorTest test`
Expected: FAIL until React is factory-driven.

**Step 3: Write minimal implementation**

Route React entry-state creation and model message construction through `ReactAgentContextFactory`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,SingleAgentOrchestratorTest test`
Expected: PASS

### Task 4: Migrate Planning and Reflexion orchestrator entry assembly

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving plan-step state and reflexion review state are assembled through paradigm factories, not hand-built in orchestrators.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest test`
Expected: FAIL until orchestrators are factory-driven.

**Step 3: Write minimal implementation**

Move planning-step and reflexion actor/critic entry construction into the new factories.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest test`
Expected: PASS

### Task 5: Wire factories and run targeted regression verification

**Files:**
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: relevant factory/agent/orchestrator files from Tasks 1-4

**Step 1: Run targeted regression suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningExecutionRuntimeTest,PlanningThenExecutionOrchestratorTest,ReactAgentTest,SingleAgentOrchestratorTest,ReflexionCriticDefinitionTest,ReflexionOrchestratorTest test`
Expected: PASS

**Step 2: Run broader nearby verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningExecutionRuntimeTest,PlanningThenExecutionOrchestratorTest,PlanningAgentImplTest,ReactAgentTest,SingleAgentOrchestratorTest,ReflexionCriticDefinitionTest,ReflexionOrchestratorTest test`
Expected: PASS

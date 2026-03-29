# Supervisor Context Factory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce a dedicated `SupervisorContextFactory` so supervisor context assembly, routing invocation input construction, worker execution context setup, and worker result summarization all live outside `SupervisorOrchestrator` and `HybridSupervisorAgent`.

**Architecture:** Add a supervisor-specific `AgentContextFactory` implementation that owns the supervisor conversation state lifecycle and routing-model invocation rendering. Simplify `SupervisorOrchestrator` down to loop control and runtime delegation, and simplify `HybridSupervisorAgent` down to decision rules plus model result interpretation.

**Tech Stack:** Java 17, Spring Boot, Lombok, JUnit 5, Mockito, LangChain4j

---

### Task 1: Lock In Supervisor Context Factory Behavior

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`

**Step 1: Write the failing test**

Add tests for:
- `prepareInitialContext` preserving request memory and current document content
- `buildSupervisorContext` producing snapshot copies of workers and worker results
- `buildWorkerExecutionContext` keeping only summary memory
- `summarizeWorkerResult` appending `Previous worker result`
- `buildRoutingInvocationContext` rendering candidates and worker result summaries

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest test`

Expected: FAIL because `SupervisorContextFactory` does not exist yet.

**Step 3: Write minimal implementation**

Create a skeletal `SupervisorContextFactory` with the tested methods and minimal logic to satisfy compilation.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java
git commit -m "test: add supervisor context factory coverage"
```

### Task 2: Move Orchestrator Context Assembly Into Factory

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`

**Step 1: Write the failing test**

Update `SupervisorOrchestratorTest` to assert:
- orchestrator uses factory-built worker execution context
- supervisor context snapshots remain stable across repeated worker turns
- worker summary memory still excludes tool transcripts

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest test`

Expected: FAIL because orchestrator still owns context assembly.

**Step 3: Write minimal implementation**

Change `SupervisorOrchestrator` to:
- depend on `SupervisorContextFactory`
- use `prepareInitialContext`
- use `buildSupervisorContext`
- use `buildWorkerExecutionContext`
- use `summarizeWorkerResult`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java
git commit -m "refactor: move supervisor orchestration context assembly into factory"
```

### Task 3: Move Routing Invocation Rendering Out Of HybridSupervisorAgent

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`

**Step 1: Write the failing test**

Update `HybridSupervisorAgentTest` to assert:
- routing model receives factory-rendered candidate text
- routing model receives factory-rendered worker result summary
- fallback instruction comes from factory helper rather than agent-local string assembly

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=HybridSupervisorAgentTest test`

Expected: FAIL because `HybridSupervisorAgent` still builds invocation strings itself.

**Step 3: Write minimal implementation**

Change `HybridSupervisorAgent` to:
- accept `SupervisorContextFactory`
- request `ModelInvocationContext` or rendered routing inputs from the factory
- remove local `renderCandidates`, `renderWorkerResults`, and local fallback instruction assembly

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=HybridSupervisorAgentTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java
git commit -m "refactor: move supervisor routing context rendering into factory"
```

### Task 4: Wire Supervisor Factory Through Spring Configuration

**Files:**
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Update configuration test to assert:
- `SupervisorContextFactory` bean exists
- `HybridSupervisorAgent` is wired with factory
- `SupervisorOrchestrator` is wired with factory

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest test`

Expected: FAIL because the factory bean is not registered yet.

**Step 3: Write minimal implementation**

Register `SupervisorContextFactory` as a bean and inject it into:
- `HybridSupervisorAgent`
- `SupervisorOrchestrator`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SupervisorOrchestratorTest,HybridSupervisorAgentTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java
git commit -m "refactor: wire supervisor context factory through configuration"
```

### Task 5: Run Supervisor Regression Verification

**Files:**
- Modify: none unless regressions appear
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Run targeted regression suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,SupervisorOrchestratorTest,HybridSupervisorAgentTest,AgentV2ConfigurationSplitTest test
```

Expected: PASS

**Step 2: Fix any supervisor-path regressions**

If failures appear, make the minimal change in the touched supervisor files only.

**Step 3: Re-run the targeted regression suite**

Run the same command again until green.

**Step 4: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor src/main/java/com/agent/editor/config src/test/java/com/agent/editor/agent/v2/supervisor src/test/java/com/agent/editor/config
git commit -m "refactor: centralize supervisor context assembly"
```

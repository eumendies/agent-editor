# Remove Trace Calls Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove all current trace-producing calls from business execution code while keeping trace infrastructure available for reads and future reuse.

**Architecture:** The change is a write-side removal only. Runtime/orchestrator/agent classes stop depending on `TraceCollector`, Spring wiring shrinks accordingly, and tests move from trace assertions to business-behavior assertions.

**Tech Stack:** Java 17, Spring Boot, Maven, JUnit 5, Mockito, Lombok

---

### Task 1: Remove orchestrator and runtime trace writes

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`

**Step 1: Remove `TraceCollector` fields, constructor params, imports, helper methods, and `traceCollector.collect(...)` calls from the four runtime/orchestrator classes.**

**Step 2: Update affected tests to stop building trace stores only for orchestration assertions.**

**Step 3: Replace trace assertions with business assertions around state, result, memory, and published events.**

**Step 4: Run targeted tests.**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ToolLoopExecutionRuntimeTest,PlanningThenExecutionOrchestratorTest,SupervisorOrchestratorTest,ReflexionOrchestratorTest test
```

Expected: PASS

### Task 2: Remove agent-definition trace writes

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentDefinitionTest.java`

**Step 1: Remove `TraceCollector` constructor dependencies, fields, imports, helper methods, and write calls from these agent definitions.**

**Step 2: Update tests to construct these definitions without trace dependencies.**

**Step 3: Keep behavior coverage focused on prompts, tool-call parsing, and toolLoopDecision outputs.**

**Step 4: Run targeted tests.**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReactAgentDefinitionTest,ReflexionCriticDefinitionTest,ResearcherAgentDefinitionTest,GroundedWriterAgentDefinitionTest,EvidenceReviewerAgentDefinitionTest test
```

Expected: PASS

### Task 3: Fix Spring wiring and integration tests

**Files:**
- Modify: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ReflexionAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Update bean creation to match the new constructor signatures without `TraceCollector`.**

**Step 2: Verify no trace-only dependency remains in write-side bean wiring.**

**Step 3: Run focused configuration tests.**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigurationSplitTest test
```

Expected: PASS

### Task 4: Remove remaining trace-write assertions and verify no write calls remain

**Files:**
- Modify tests still asserting trace stage/payload behavior if any remain after Tasks 1-3

**Step 1: Search for remaining write-side trace calls.**

Run:
```bash
rg -n "traceCollector\\.collect|new TraceRecord\\(" src/main/java
```

Expected: only trace infrastructure/support code if any, and no runtime/orchestrator/agent business-path write sites

**Step 2: Search for tests still asserting trace creation side effects.**

Run:
```bash
rg -n "TraceCategory|traceStore|getByTaskId|payload\\.|stage\\(" src/test/java
```

**Step 3: Update any remaining tests that still expect runtime-generated traces.**

**Step 4: Run full test suite.**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test
```

Expected: BUILD SUCCESS

### Task 5: Final verification and commit

**Files:**
- Review all modified files from previous tasks

**Step 1: Re-run a final search for write-side trace calls and confirm the remaining trace package is read-side compatible.**

**Step 2: Re-run full tests if any code changed after Task 4.**

**Step 3: Commit with a focused message.**

```bash
git add src/main/java src/test/java
git commit -m "refactor: remove runtime trace emissions"
```

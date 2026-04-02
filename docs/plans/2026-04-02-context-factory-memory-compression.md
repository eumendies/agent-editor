# Context Factory Memory Compression Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace repeated explicit memory compression calls in selected agent v2 context factory methods with Spring AOP while preserving the special compression order in `SupervisorContextFactory.buildWorkerExecutionContext(...)`.

**Architecture:** Add an explicit method-level annotation and a small capability interface, then apply an `@Around` aspect that compresses returned `AgentRunContext` values for annotated Spring bean methods. Keep the supervisor worker-context builder on manual compression because its ordering is intentionally different.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring AOP, JUnit 5, AssertJ

---

### Task 1: Add failing proxy-based compression test

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/core/context/ContextMemoryCompressionAspectTest.java`
- Check: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`

**Step 1: Write the failing test**

Create a Spring test that:
- registers a stub `MemoryCompressor`
- registers `ReactAgentContextFactory` and `SupervisorContextFactory` as beans
- calls `reactAgentContextFactory.prepareInitialContext(...)` and expects compressed memory
- calls `supervisorContextFactory.buildWorkerExecutionContext(...)` and expects the current explicit sequencing

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ContextMemoryCompressionAspectTest test
```

Expected:
- test fails because AOP compression is not yet active

**Step 3: Write minimal implementation**

Add the annotation, capability interface, and aspect; add AOP dependency; annotate the whitelist methods; remove explicit compression calls from those methods.

**Step 4: Run test to verify it passes**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ContextMemoryCompressionAspectTest test
```

Expected:
- test passes

**Step 5: Commit**

```bash
git add pom.xml src/main/java src/test/java docs/plans/2026-04-02-context-factory-memory-compression-design.md docs/plans/2026-04-02-context-factory-memory-compression.md
git commit -m "refactor: apply context memory compression via aop"
```

### Task 2: Update existing factory tests for the new boundary

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`

**Step 1: Write the failing test adjustments**

Update assertions so tests only rely on direct construction where that remains valid, and move proxy/compression expectations into Spring-backed tests where necessary.

**Step 2: Run focused tests to verify failures are meaningful**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,ReflexionActorContextFactoryTest,ReflexionCriticContextFactoryTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,SupervisorContextFactoryTest test
```

Expected:
- failures only reflect the new AOP boundary assumptions

**Step 3: Write minimal implementation**

Adjust tests and helper setup to assert the intended post-refactor behavior without depending on implicit direct-call compression.

**Step 4: Run focused tests to verify they pass**

Run the same command and expect all listed tests to pass.

**Step 5: Commit**

```bash
git add src/test/java
git commit -m "test: align context factory coverage with aop compression"
```

### Task 3: Run regression verification

**Files:**
- Check: `pom.xml`
- Check: `src/main/java/com/agent/editor/agent/v2/core/context`
- Check: `src/main/java/com/agent/editor/agent/v2/react`
- Check: `src/main/java/com/agent/editor/agent/v2/planning`
- Check: `src/main/java/com/agent/editor/agent/v2/reflexion`
- Check: `src/main/java/com/agent/editor/agent/v2/supervisor`
- Check: `src/main/java/com/agent/editor/agent/v2/supervisor/worker`

**Step 1: Run targeted agent-v2 regression tests**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ContextMemoryCompressionAspectTest,ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,ReflexionActorContextFactoryTest,ReflexionCriticContextFactoryTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,SupervisorContextFactoryTest,AgentV2ConfigurationSplitTest test
```

Expected:
- all targeted tests pass

**Step 2: Run broader suite if time allows**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected:
- full suite passes

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: route context memory compression through aspect"
```

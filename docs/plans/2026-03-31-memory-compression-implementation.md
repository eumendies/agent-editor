# Memory Compression Runtime Write-Back Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move memory compression into `ContextFactory` runtime-context creation so compressed transcripts are written back into `AgentRunContext`, and remove test-only no-op compressor code from production classes.

**Architecture:** Add explicit runtime-memory replacement support to `AgentRunContext`, shift compression from `buildModelInvocationContext(...)` into all `ContextFactory` methods that create or derive contexts, and make `buildModelInvocationContext(...)` a pure mapping step. Delete `passthrough`/no-arg fallback production paths and force tests to pass explicit stub compressors.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, JUnit 5, Mockito

---

### Task 1: Lock Down Runtime Memory Replacement On AgentRunContext

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/context/AgentRunContext.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/AgentRunContextTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/AgentRunContextTest.java`

**Step 1: Write the failing test**

Add a focused test proving `AgentRunContext` can return a new instance with replaced memory while preserving:

- `request`
- `iteration`
- `currentContent`
- `stage`
- `pendingReason`
- `toolSpecifications`

Name it around `withMemory`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentRunContextTest test`

Expected: FAIL because `withMemory(...)` does not exist yet.

**Step 3: Write minimal implementation**

Add:

```java
public AgentRunContext withMemory(ExecutionMemory nextMemory) {
    return new AgentRunContext(request, iteration, currentContent, nextMemory, stage, pendingReason, toolSpecifications);
}
```

Keep constructor validation behavior unchanged.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentRunContextTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/context/AgentRunContext.java \
        src/test/java/com/agent/editor/agent/v2/core/runtime/AgentRunContextTest.java
git commit -m "refactor: add runtime memory replacement on agent context"
```

### Task 2: Make MemoryCompressor The Only Shared Compression Wrapper

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/memory/MemoryCompressor.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/memory/MemoryCompressionRequest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/memory/ModelBasedMemoryCompressor.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/memory/MemoryPackageStructureTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/memory/ModelBasedMemoryCompressorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/memory/MemoryPackageStructureTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/memory/ModelBasedMemoryCompressorTest.java`

**Step 1: Write the failing tests**

Add or update tests to prove:

- `MemoryCompressor` exposes a shared `compressOrOriginal(...)` convenience method
- `preserveLatestMessageCount` can be omitted from the request and then falls back to configured defaults inside `ModelBasedMemoryCompressor`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryPackageStructureTest,ModelBasedMemoryCompressorTest test`

Expected: FAIL because the API does not yet support the new behavior.

**Step 3: Write minimal implementation**

- Keep shared transcript wrapping logic inside `MemoryCompressor`
- Make `preserveLatestMessageCount` nullable in `MemoryCompressionRequest`
- Update `ModelBasedMemoryCompressor` to use config defaults when that field is null or non-positive

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryPackageStructureTest,ModelBasedMemoryCompressorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/memory/MemoryCompressor.java \
        src/main/java/com/agent/editor/agent/v2/core/memory/MemoryCompressionRequest.java \
        src/main/java/com/agent/editor/agent/v2/memory/ModelBasedMemoryCompressor.java \
        src/test/java/com/agent/editor/agent/v2/memory/MemoryPackageStructureTest.java \
        src/test/java/com/agent/editor/agent/v2/memory/ModelBasedMemoryCompressorTest.java
git commit -m "refactor: centralize transcript compression wrapper"
```

### Task 3: Move Compression Into React/Planning/Reflexion Context Construction

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`

**Step 1: Write the failing tests**

Add assertions that:

- `prepareInitialContext(...)` returns `AgentRunContext` whose memory is already compressed
- planning step/review/revision context-derivation methods also return compressed runtime memory
- `buildModelInvocationContext(...)` no longer needs to trigger compression and simply reflects the existing context memory

Keep the tests focused on observable context state rather than internal method calls.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,ReflexionCriticContextFactoryTest test`

Expected: FAIL because compression still occurs only during message mapping.

**Step 3: Write minimal implementation**

In each factory:

- require a `MemoryCompressor` constructor dependency
- add a small internal method that returns `context.withMemory(memoryCompressor.compressOrOriginal(...))`
- call it from every context-creation/derivation method
- remove compression calls from `buildModelInvocationContext(...)`

For `ReflexionActorContextFactory`, ensure revised contexts also carry compressed memory after feedback is appended.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,ReflexionCriticContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java
git commit -m "refactor: compress runtime context in core factories"
```

### Task 4: Move Compression Into Supervisor Context Construction

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`

**Step 1: Write the failing tests**

Add assertions proving:

- supervisor-derived worker execution contexts already contain compressed memory
- worker context factories no longer perform fresh compression during invocation mapping
- summary-only filtering still preserves `lastObservedTotalTokens`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest test`

Expected: FAIL because compression is still happening in invocation mapping or not written back into runtime contexts.

**Step 3: Write minimal implementation**

- Move compression into supervisor context-creation/derivation methods
- Keep worker-specific summary filtering after compression where needed
- Remove compression from worker `buildModelInvocationContext(...)` methods

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java
git commit -m "refactor: compress runtime context in supervisor flows"
```

### Task 5: Remove Production No-Op Constructors And passthrough Helpers

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`

**Step 1: Write the failing test**

Pick one representative test per family and update it to construct the factory with an explicit stub `MemoryCompressor`. Then remove the no-arg constructor usage.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,SupervisorContextFactoryTest test`

Expected: FAIL because production classes still expose no-arg constructors or private `passthrough` helpers.

**Step 3: Write minimal implementation**

- delete no-arg constructors whose only purpose is to inject no-op compression
- delete `passthrough` helper methods
- keep tests explicit by passing stub compressor lambdas

Do not add a production `NOOP` constant just for tests.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionActorContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java
git commit -m "refactor: remove noop compression scaffolding"
```

### Task 6: Verify Persistence Still Receives Compressed Transcript

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestrator.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestratorTest.java`

**Step 1: Write the failing test**

Add or refine a test proving:

- when delegate returns transcript memory already compressed in runtime flow, save still persists that compact transcript
- save-time compression, if retained as a temporary safety net, does not regress persisted output

If you decide to remove save-time compression during implementation, the test must prove the returned runtime memory is already compact before save.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryTaskOrchestratorTest test`

Expected: FAIL if persistence behavior no longer matches the chosen steady-state design.

**Step 3: Write minimal implementation**

Choose one steady-state rule and encode it clearly:

- either keep save-time compression as temporary fallback
- or remove it after proving all returned runtime memories are already compressed

Document the choice with a short Chinese comment if the branch is non-obvious.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryTaskOrchestratorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestrator.java \
        src/test/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestratorTest.java
git commit -m "refactor: align session persistence with runtime compression"
```

### Task 7: Full Regression Verification

**Files:**
- No code changes expected

**Step 1: Run focused regression suites**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn \
  -Dtest=AgentV2ConfigurationSplitTest,MemoryPackageStructureTest,AgentRunContextTest,ModelBasedMemoryCompressorTest,ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,ReflexionCriticContextFactoryTest,SupervisorContextFactoryTest,SessionMemoryTaskOrchestratorTest,ReactAgentTest,HybridSupervisorAgentTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest test
```

Expected: PASS with `0` failures.

**Step 2: Inspect diff**

Run:

```bash
git diff -- src/main/java/com/agent/editor/agent/v2 src/test/java/com/agent/editor/agent/v2 docs/plans
```

Confirm no `passthrough` production helpers or no-arg compression constructors remain in touched factory classes.

**Step 3: Commit final verification cleanup if needed**

If no changes are needed, skip commit. If tiny follow-up fixes were required, commit them with:

```bash
git add <touched files>
git commit -m "test: finalize runtime compression refactor"
```

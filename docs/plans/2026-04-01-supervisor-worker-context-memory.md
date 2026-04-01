# Supervisor Worker Context Memory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make supervisor and worker context assembly preserve the current user instruction in transcript memory and keep model-visible message order consistent.

**Architecture:** Normalize context assembly around transcript-first prompts. Standalone factories append the current instruction during initial context creation, and the supervisor runtime seeds each worker run with a fresh transcript snapshot that also includes that worker's current instruction. Prompt builders then render `system + transcript` instead of mixing ad hoc user-message insertion with historical memory.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Maven

---

### Task 1: Lock in failing context-factory expectations

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`

**Step 1: Write the failing tests**

Add tests that assert:
- `prepareInitialContext()` appends the current instruction into transcript memory before compression-sensitive handoff.
- `buildModelInvocationContext()` renders `system + transcript` without inserting a duplicate instruction ahead of older transcript entries.
- `buildWorkerExecutionContext()` includes the current worker instruction in the seeded transcript.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=EvidenceReviewerAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,ResearcherAgentContextFactoryTest,SupervisorContextFactoryTest test`

Expected: failures showing missing current instruction in memory or wrong prompt ordering.

### Task 2: Normalize context assembly code

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`

**Step 1: Write minimal implementation**

Change the factories so they:
- append the current instruction when constructing initial transcript memory;
- build invocation messages from transcript memory only after the system prompt;
- seed supervisor worker execution contexts with the assigned worker instruction.

Keep helper logic small and shared only where it reduces duplication without introducing new abstractions that are not yet needed.

**Step 2: Run targeted tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=EvidenceReviewerAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,ResearcherAgentContextFactoryTest,SupervisorContextFactoryTest test`

Expected: PASS.

### Task 3: Prove the supervisor path now preserves session-visible user turns

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write the failing test**

Add a regression test asserting the final `TaskResult` memory returned by `SupervisorOrchestrator` still contains the current top-level user instruction, not only worker summaries.

**Step 2: Run the single test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest#shouldRetainCurrentUserInstructionInReturnedSessionMemory test`

Expected: failure because the returned memory lacks the current instruction.

**Step 3: Re-run after implementation**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest test`

Expected: PASS.

### Task 4: Focused regression verification

**Files:**
- No code changes expected

**Step 1: Run the focused suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorOrchestratorTest,SupervisorContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,ResearcherAgentContextFactoryTest,ReactAgentContextFactoryTest test`

Expected: PASS.

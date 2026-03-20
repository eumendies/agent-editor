# Session Memory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist multi-turn conversation memory by `sessionId` with an in-memory store and keep `Complete/Respond` answers in transcript memory.

**Architecture:** A task-orchestrator decorator loads prior `ChatTranscriptMemory` by `sessionId`, injects it into the top-level task request, and saves the final memory after execution. `DefaultExecutionRuntime` remains responsible for building the per-run transcript by appending user, tool-call, tool-result, and final assistant messages.

**Tech Stack:** Java 17, Spring Boot, LangChain4j 1.11.0, JUnit 5

---

### Task 1: Write failing runtime tests for user/complete transcript messages

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**
- Assert that a normal completion run appends `UserChatMessage` and `AiChatMessage` to final memory.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DefaultExecutionRuntimeTest test`

**Step 3: Write minimal implementation**
- Append user message at run start and ai message on `Complete`/`Respond`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DefaultExecutionRuntimeTest test`

### Task 2: Write failing session-memory orchestration tests

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/task/RoutingTaskOrchestratorTest.java`

**Step 1: Write the failing test**
- Assert that prior memory is loaded by `sessionId`, passed into the delegate orchestrator, and final memory is saved back.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryTaskOrchestratorTest test`

**Step 3: Write minimal implementation**
- Add `SessionMemoryStore`, `InMemorySessionMemoryStore`, and `SessionMemoryTaskOrchestrator`.
- Extend task contracts with memory in/out.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryTaskOrchestratorTest test`

### Task 3: Propagate memory through top-level orchestrators

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`

**Step 1: Write failing tests or update existing ones**
- Assert top-level orchestrators accept prior memory and return final memory.

**Step 2: Implement the minimal propagation**
- Seed initial execution state from request memory where applicable.
- Return final memory via `TaskResult`.
- Wrap the routing orchestrator with `SessionMemoryTaskOrchestrator`.

**Step 3: Run focused verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DefaultExecutionRuntimeTest,SessionMemoryTaskOrchestratorTest,RoutingTaskOrchestratorTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest test`

**Step 4: Run full verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

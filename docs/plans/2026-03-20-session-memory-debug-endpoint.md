# Session Memory Debug Endpoint Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a debug endpoint that returns structured session memory by `sessionId`.

**Architecture:** Keep session-memory storage unchanged and add a read-only query path. A small service maps internal `ChatMessage` objects to stable API DTOs, and `AgentController` exposes the endpoint under the existing agent API surface.

**Tech Stack:** Java 17, Spring Boot, JUnit 5

---

### Task 1: Write failing mapping tests

**Files:**
- Create: `src/test/java/com/agent/editor/service/SessionMemoryQueryServiceTest.java`

**Step 1: Write the failing test**
- Build a `ChatTranscriptMemory` containing user, ai, ai-tool-call, and tool-result messages.
- Assert the query service returns structured DTOs with correct `type`, `text`, and tool fields.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryQueryServiceTest test`

**Step 3: Write minimal implementation**
- Add DTOs and `SessionMemoryQueryService`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryQueryServiceTest test`

### Task 2: Write failing controller test

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/AgentControllerTest.java`

**Step 1: Write the failing test**
- Assert `GET /api/v1/agent/session/{sessionId}/memory` returns `200` and structured JSON for an existing session.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentControllerTest test`

**Step 3: Write minimal implementation**
- Inject the query service into `AgentController`
- Add the endpoint

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentControllerTest test`

### Task 3: Full verification

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/task/SessionMemoryStore.java`

**Step 1: Wire final read API**
- Ensure the store contract supports query use cleanly and does not expose mutable internals.

**Step 2: Run focused verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SessionMemoryQueryServiceTest,AgentControllerTest test`

**Step 3: Run full verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

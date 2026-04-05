# Memory Tool Error Envelope Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Return structured JSON errors from `memory_upsert` and `memory_search` for recoverable validation failures without changing successful responses.

**Architecture:** Keep the change at the tool boundary. Each memory tool catches recoverable `IllegalArgumentException` values raised during action/type validation or service validation, serializes a compact error envelope, and returns it in `ToolResult.message`. Malformed JSON parsing and non-recoverable internal failures remain exception-based.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Jackson

---

### Task 1: Lock `MemoryUpsertTool` failure behavior with tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`

**Step 1: Write the failing test**

Add one test for service validation failure and one test for invalid `action`, asserting:

- the tool returns JSON instead of throwing
- `status` is `error`
- `errorMessage` contains the original validation message
- `updatedContent` is `null`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryUpsertToolTest test`

Expected: FAIL because the tool still throws instead of returning error JSON

**Step 3: Write minimal implementation**

Update `MemoryUpsertTool.execute(...)` to:

- decode arguments first
- catch recoverable `IllegalArgumentException` around enum conversion and service invocation
- serialize an error envelope with request context

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryUpsertToolTest test`

Expected: PASS

### Task 2: Lock `MemorySearchTool` failure behavior with tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`

**Step 1: Write the failing test**

Add one test where `LongTermMemoryRetrievalService` throws `IllegalArgumentException`, asserting:

- the tool returns JSON instead of throwing
- `status` is `error`
- `errorMessage` preserves the service message
- request context fields are present
- `updatedContent` is `null`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemorySearchToolTest test`

Expected: FAIL because the tool still throws instead of returning error JSON

**Step 3: Write minimal implementation**

Update `MemorySearchTool.execute(...)` to:

- decode arguments first
- catch recoverable `IllegalArgumentException` around service invocation
- serialize an error envelope with request context

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemorySearchToolTest test`

Expected: PASS

### Task 3: Verify the combined regression surface

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`

**Step 1: Run the focused suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryUpsertToolTest,MemorySearchToolTest test`

Expected: PASS with the new error-envelope assertions

**Step 2: Review the diff for scope**

Confirm the change is limited to the two memory tools, their tests, and the plan/design docs.

**Step 3: Report verification evidence**

Include the exact verification command run and whether it passed.

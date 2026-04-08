# Agent V2 Recoverable Tool Errors Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Return model-correctable tool failures to the model as tool-result messages instead of throwing them out of the `agent.v2` tool loop.

**Architecture:** Add a dedicated `RecoverableToolException` to mark model-recoverable tool failures. Move error-envelope serialization into `ToolLoopExecutionRuntime`, where missing/disallowed tools and handler-thrown recoverable failures are converted into structured error `ToolResult` messages while non-recoverable failures still abort execution.

**Tech Stack:** Java 17, Spring Boot, Lombok, Jackson, JUnit 5, Maven

---

### Task 1: Add red runtime tests for recoverable tool failures

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Write the missing/disallowed tool recovery test**

Add a test where the agent first requests a tool that is not allowed, then completes after reading the resulting tool error message from memory.

**Step 2: Write the recoverable handler failure test**

Add a test where a registered tool throws `RecoverableToolException` and the runtime converts it into a tool result instead of aborting.

**Step 3: Keep the unrecoverable failure test**

Add or preserve a test showing that an ordinary `RuntimeException` from a tool still propagates and fails execution.

**Step 4: Run the focused runtime test to verify it fails**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest test
```

Expected: FAIL because the runtime currently throws for missing/disallowed tools and does not understand `RecoverableToolException`.

### Task 2: Implement runtime-level recoverable failure conversion

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/RecoverableToolException.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`

**Step 1: Add the recoverable exception type**

Create a focused runtime exception with factory helpers for recoverable tool failures.

**Step 2: Add a runtime error-envelope serializer**

Inside `ToolLoopExecutionRuntime`, add a small JSON payload type for recoverable tool errors containing:

- `status`
- `tool`
- `errorMessage`
- `arguments`

**Step 3: Convert missing/disallowed tools into recoverable results**

Replace the current `IllegalStateException` path with a `ToolResult` carrying the serialized error envelope.

**Step 4: Catch `RecoverableToolException` from handlers**

Wrap each tool execution so recoverable failures become error `ToolResult` instances and ordinary exceptions still propagate.

**Step 5: Re-run the focused runtime test**

Run the same command from Task 1 Step 4.

Expected: PASS.

### Task 3: Move document and memory tools onto the typed exception boundary

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ToolArgumentDecoder.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`

**Step 1: Make argument decoding throw `RecoverableToolException`**

Update decoder/helper code so malformed JSON is explicitly marked recoverable.

**Step 2: Replace tool-local error `ToolResult` creation**

For domain validation failures, throw `RecoverableToolException` instead of returning a JSON error payload directly.

**Step 3: Keep unrecoverable failures loud**

Do not wrap serialization failures or unexpected exceptions.

**Step 4: Add concise Chinese comments where the recovery boundary is subtle**

Document why these exceptions are intentionally reclassified for runtime consumption.

### Task 4: Update tool tests to the new exception boundary

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`

**Step 1: Replace old JSON-error assertions with exception assertions**

For recoverable failures, assert that tools now throw `RecoverableToolException`.

**Step 2: Keep success-path assertions unchanged**

Do not weaken existing success expectations.

**Step 3: Add at least one malformed-JSON assertion through the new typed exception**

Verify the decoder path also uses `RecoverableToolException`.

### Task 5: Run the regression slice and confirm completion

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ToolArgumentDecoder.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemorySearchTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertTool.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemorySearchToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryUpsertToolTest.java`

**Step 1: Run the focused recoverable-failure test slice**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,ReadDocumentNodeToolTest,PatchDocumentNodeToolTest,MemorySearchToolTest,MemoryUpsertToolTest test
```

Expected: PASS.

**Step 2: Run a broader `agent.v2` regression slice if needed**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredDocumentToolContextTest,ToolLoopExecutionRuntimeTest,ReadDocumentNodeToolTest,PatchDocumentNodeToolTest,MemorySearchToolTest,MemoryUpsertToolTest test
```

Expected: PASS.

**Step 3: Review the diff for accidental swallowing of unrecoverable failures**

Check that only explicit recoverable paths were converted.

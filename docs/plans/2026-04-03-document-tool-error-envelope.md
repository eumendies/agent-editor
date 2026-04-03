# Document Tool Error Envelope Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `readDocumentNode` and `patchDocumentNode` return structured JSON errors for recoverable document-service validation failures instead of aborting the agent runtime.

**Architecture:** Keep `StructuredDocumentService` behavior unchanged and move the recovery boundary into `ReadDocumentNodeTool` and `PatchDocumentNodeTool`. Each tool will catch `IllegalArgumentException` and serialize a small JSON error envelope through `ToolResult.message`, preserving the original validation message and request context while leaving success responses unchanged.

**Tech Stack:** Java 17, Spring Boot, Lombok, Jackson, JUnit 5, Maven

---

### Task 1: Add red tests for `ReadDocumentNodeTool` recoverable failures

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`

**Step 1: Write the failing unknown-node test**

Add a test that invokes `readDocumentNode` with a non-existent `nodeId` and asserts the tool returns a JSON error payload with:

- `status = "error"`
- `errorMessage = "Unknown nodeId: ..."`
- `nodeId` equal to the requested id
- `updatedContent` equal to `null`

**Step 2: Write the failing invalid-mode test**

Add a test that invokes the tool with an unsupported `mode` and asserts:

- `status = "error"`
- `errorMessage` contains the invalid mode
- `updatedContent` equal to `null`

**Step 3: Write the failing invalid-block test**

Use an overflowing node and request a missing `blockId`, then assert:

- `status = "error"`
- `errorMessage = "Unknown blockId: ..."`
- `blockId` equal to the requested id

**Step 4: Run the focused read-tool test to verify it fails**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReadDocumentNodeToolTest test
```

Expected: FAIL because the tool currently propagates `IllegalArgumentException`.

### Task 2: Implement `ReadDocumentNodeTool` error envelopes

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java`

**Step 1: Add a small error response bean**

Add a private static response type with:

- `status`
- `errorMessage`
- `nodeId`
- `blockId`
- `operation`

Use Lombok bean style to match repository conventions.

**Step 2: Wrap the service call with recoverable failure handling**

In `execute(...)`:

- decode arguments as today
- keep the success path unchanged
- catch `IllegalArgumentException`
- serialize the error response into `ToolResult.message`

**Step 3: Keep the original validation message intact**

Do not add a string-to-code mapping layer. The error payload should preserve the original `IllegalArgumentException` message in `errorMessage`.

**Step 4: Re-run the focused read-tool test**

Run the same command from Task 1 Step 4.

Expected: PASS.

**Step 5: Commit the read-tool milestone**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java
git commit -m "fix: return read tool validation errors as json"
```

### Task 3: Add red tests for `PatchDocumentNodeTool` recoverable failures

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`

**Step 1: Write the failing unknown-node test**

Invoke `patchDocumentNode` with a missing `nodeId` and assert:

- `status = "error"`
- `errorMessage = "Unknown nodeId: ..."`
- `operation` equals the requested operation
- `updatedContent` equals `null`

**Step 2: Write the failing invalid-operation test**

Invoke the patch tool with an unsupported `operation` and assert:

- `status = "error"`
- `errorMessage` contains the invalid operation
- `operation` equals the requested operation

**Step 3: Write the failing invalid replacement-content test**

Use `replace_node` with content that violates the one-top-level-heading rule and assert:

- `status = "error"`
- `errorMessage` equals the original validation message
- `updatedContent` equals `null`

**Step 4: Run the focused patch-tool test to verify it fails**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PatchDocumentNodeToolTest test
```

Expected: FAIL because the tool currently propagates `IllegalArgumentException`.

### Task 4: Implement `PatchDocumentNodeTool` error envelopes

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java`

**Step 1: Add a small error response bean**

Add a private static response type matching the read tool:

- `status`
- `errorMessage`
- `nodeId`
- `blockId`
- `operation`

**Step 2: Wrap the service call with recoverable failure handling**

In `execute(...)`:

- keep the current success payload unchanged
- catch `IllegalArgumentException`
- return error JSON without `updatedContent`

**Step 3: Keep the original validation message intact**

Do not add a string-to-code mapping layer. The error payload should preserve the original `IllegalArgumentException` message in `errorMessage`.

**Step 4: Re-run the focused patch-tool test**

Run the same command from Task 3 Step 4.

Expected: PASS.

### Task 5: Run the combined regression slice and capture completion state

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`

**Step 1: Run both focused tool test classes**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReadDocumentNodeToolTest,PatchDocumentNodeToolTest test
```

Expected: PASS.

**Step 2: Run the related structured-document test slice**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredDocumentServiceTest,ReadDocumentNodeToolTest,PatchDocumentNodeToolTest test
```

Expected: PASS.

**Step 3: Commit the patch-tool milestone**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java \
        docs/plans/2026-04-03-document-tool-error-envelope-design.md \
        docs/plans/2026-04-03-document-tool-error-envelope.md
git commit -m "fix: return structured document tool validation errors"
```

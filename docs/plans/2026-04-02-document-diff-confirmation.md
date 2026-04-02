# Document Diff Confirmation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make agent-produced document changes wait for explicit user confirmation before they are written into the document.

**Architecture:** Add a dedicated pending document change service that stores one reviewable candidate per document. Update task completion to save pending changes instead of mutating committed documents, expose apply/discard endpoints through the diff controller, and update the demo page so the diff panel drives confirmation while the editor continues to show saved content.

**Tech Stack:** Spring Boot, in-memory services, controller DTOs, Thymeleaf template HTML, inline browser JavaScript, JUnit 5, Mockito

---

### Task 1: Add failing tests for pending-change backend behavior

**Files:**
- Create: `src/test/java/com/agent/editor/service/PendingDocumentChangeServiceTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing service test**

Add tests that verify:

- saving a pending change makes it readable by `documentId`
- saving a second pending change for the same document replaces the first one
- discarding removes the pending change

**Step 2: Write the failing task-application tests**

Add tests that verify:

- task completion no longer updates `DocumentService` immediately
- task completion stores a pending change containing the final content
- applying the pending change updates the document
- discarding the pending change leaves the document unchanged

**Step 3: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PendingDocumentChangeServiceTest,TaskApplicationServiceTest test`
Expected: FAIL because pending-change types and apply/discard behavior do not exist yet.

### Task 2: Implement the pending-change model and service

**Files:**
- Create: `src/main/java/com/agent/editor/dto/PendingDocumentChange.java`
- Create: `src/main/java/com/agent/editor/service/PendingDocumentChangeService.java`
- Modify: `src/main/java/com/agent/editor/service/DiffService.java`

**Step 1: Add the DTO**

Implement a bean with:

- `documentId`
- `taskId`
- `originalContent`
- `proposedContent`
- `diffHtml`
- `createdAt`

**Step 2: Add the service**

Implement methods that:

- create or replace the pending change for a document
- return the current pending change for a document
- remove the pending change for a document

Use `ConcurrentHashMap` storage keyed by `documentId`.

**Step 3: Keep diff generation centralized**

Use `DiffService.generateDiff(...)` when building a pending change so diff markup is created in one place.

**Step 4: Re-run the focused tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PendingDocumentChangeServiceTest,TaskApplicationServiceTest test`
Expected: still FAIL because task completion and apply/discard paths are not wired yet.

### Task 3: Route task completion into pending review and add apply/discard application logic

**Files:**
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/state/TaskState.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Inject the pending-change service**

Update `TaskApplicationService` constructor dependencies to include `PendingDocumentChangeService`.

**Step 2: Change completion behavior**

When `result.getFinalContent()` is non-null:

- save a pending change with the original snapshot and final content
- do not update the document immediately
- do not record applied diff history yet

**Step 3: Add explicit application methods**

Add application-service methods such as:

- `getPendingDocumentChange(String documentId)`
- `applyPendingDocumentChange(String documentId)`
- `discardPendingDocumentChange(String documentId)`

Apply should:

- load the pending change
- update the document
- record applied diff history via `DiffService`
- remove the pending change

Discard should only remove the pending change.

**Step 4: Decide response shape for missing pending changes**

Use `null` return or an exception that controllers can translate into `404`.

**Step 5: Re-run the focused tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test`
Expected: PASS

### Task 4: Add failing controller tests for pending diff review endpoints

**Files:**
- Create: `src/test/java/com/agent/editor/controller/DiffControllerTest.java`

**Step 1: Write the failing controller tests**

Add tests for:

- `GET /pending` equivalent controller method returns the pending change
- apply delegates to `TaskApplicationService` and returns `200`
- discard delegates to `TaskApplicationService` and returns `204`
- missing pending change returns `404`

**Step 2: Run the focused controller test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DiffControllerTest test`
Expected: FAIL because the controller methods do not exist yet.

### Task 5: Implement pending diff controller endpoints

**Files:**
- Modify: `src/main/java/com/agent/editor/controller/DiffController.java`
- Modify: `src/test/java/com/agent/editor/controller/DiffControllerTest.java`

**Step 1: Switch controller dependencies to the correct services**

Use:

- `DiffService` for applied diff history and ad hoc compare
- `TaskApplicationService` for pending read/apply/discard

**Step 2: Add pending read endpoint**

Add a controller method for `GET /api/v1/diff/document/{documentId}/pending`.

**Step 3: Add apply endpoint**

Add a controller method for `POST /api/v1/diff/document/{documentId}/apply`.

**Step 4: Add discard endpoint**

Add a controller method for `DELETE /api/v1/diff/document/{documentId}/pending`.

**Step 5: Re-run the controller tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DiffControllerTest test`
Expected: PASS

### Task 6: Add failing template coverage for pending diff review UI

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing template assertions**

Add assertions for:

- `loadPendingDiff`
- `applyPendingDiff`
- `discardPendingDiff`
- `pendingDiffActions`
- absence of `refreshDocument(currentTaskDocumentId)` inside task completion handling

**Step 2: Run the focused template test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: FAIL because the template still refreshes the editor immediately and has no review actions.

### Task 7: Implement the review-first demo page flow

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Add pending review UI hooks**

Add:

- pending status copy in the diff panel
- action buttons for apply and discard
- an action container that can be hidden when no pending change exists

**Step 2: Split diff loading responsibilities**

Implement a `loadPendingDiff(documentId)` helper and keep history loading separate if still needed.

**Step 3: Update task finalization**

In `finalizeTask(...)`:

- stop refreshing the editor on successful completion
- load the pending diff instead
- render a status message that the candidate change is awaiting confirmation

**Step 4: Add apply/discard actions**

Implement:

- `applyPendingDiff()` to call the apply endpoint, refresh the document, then reload the pending panel
- `discardPendingDiff()` to call the discard endpoint and reload the pending panel without refreshing the editor

**Step 5: Re-run the focused template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

### Task 8: Verify the full change

**Files:**
- No additional files expected

**Step 1: Run the targeted backend and template tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PendingDocumentChangeServiceTest,TaskApplicationServiceTest,DiffControllerTest,DemoPageTemplateTest test`
Expected: PASS

**Step 2: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS

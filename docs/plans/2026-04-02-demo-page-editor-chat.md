# Demo Page Editor-Chat Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Convert the current orchestration demo page into a two-column editor/chat workbench with a manually editable document, unified chat/event panel, and retained diff plus knowledge upload flows.

**Architecture:** Keep all UI changes inside the existing Thymeleaf template and reuse current backend endpoints. Extend the template test first, then replace the page layout and inline script so the left column owns document editing and saving while the right column owns task submission, event streaming, and compact trace visibility.

**Tech Stack:** Thymeleaf template HTML, inline CSS, inline browser JavaScript, Spring Boot document/task endpoints, JUnit 5 template test

---

### Task 1: Update the template test for the new workbench structure

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Replace old assertions that lock in removed demo sections with assertions for the new workbench markers:

```java
assertTrue(template.contains("Document Workspace"));
assertTrue(template.contains("Chat Workspace"));
assertTrue(template.contains("documentEditor"));
assertTrue(template.contains("saveDocumentBtn"));
assertTrue(template.contains("saveDocument"));
assertTrue(template.contains("chatMessages"));
assertFalse(template.contains("Scenario Bar"));
assertFalse(template.contains("Mode Lens"));
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: FAIL because the existing template still contains the old layout and lacks the new workbench ids.

**Step 3: Commit nothing yet**

Do not commit until the matching template changes pass.

### Task 2: Replace the page layout and styles with the editor/chat workbench

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Remove obsolete sections**

Delete the hero intro area, the separate scenario bar layout, the standalone mode-lens card, and the dual document comparison cards.

**Step 2: Add the new main structure**

Create a two-column workbench layout with:

- left `Document Workspace` panel
- right `Chat Workspace` panel
- retained lower `Knowledge Base Upload` section

Use clear ids for the new DOM anchors:

- `documentEditor`
- `saveDocumentBtn`
- `saveStatus`
- `chatMessages`
- `clearChatBtn`

**Step 3: Adjust the styles**

Update the CSS to support:

- editor-first two-column layout
- large editable document area
- chat message list with user/assistant/system/error variants
- compact trace summary block inside the chat panel
- responsive stacking on small screens

Keep the existing visual palette rather than introducing a new design system.

**Step 4: Run the template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: reshape demo page into editor chat workbench"
```

### Task 3: Add document save flow and unsaved-change handling

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Add save state helpers**

Add script state and helper functions for:

- tracking whether the editor has unsaved changes
- rendering save status messages
- enabling/disabling the save button

Minimal helper shape:

```javascript
function markDocumentDirty(dirty) {
    isDocumentDirty = dirty;
}
```

**Step 2: Add the save request**

Implement:

```javascript
async function saveDocument() {
    // PUT /api/v1/documents/{id}?content=...
}
```

Expected behavior:

- use the selected document id
- send current `documentEditor` content
- update editor content from server response
- clear dirty state on success
- render inline error state on failure

**Step 3: Save before task execution**

Update `runMode(mode)` so it saves the editor first when dirty, and aborts the run if save fails.

**Step 4: Run the template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: add editable document save flow"
```

### Task 4: Convert execution timeline rendering into chat rendering

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Replace timeline DOM writes**

Refactor event rendering so `stepsContainer` is replaced by `chatMessages` and the event items render as chat-style message cards.

Keep the stream coalescing logic for:

- `TEXT_STREAM_STARTED`
- `TEXT_STREAM_DELTA`
- `TEXT_STREAM_COMPLETED`

But make the output read as a single assistant message in the chat feed.

**Step 2: Add explicit user and error messages**

When a run starts, append the current instruction as a user message.

When submission or runtime fails, render an error/system message in the same feed instead of only relying on console output.

**Step 3: Keep compact metadata**

Preserve:

- current mode label
- step/event count
- trace summary

But render them inside the right-side chat panel rather than in separate large cards.

**Step 4: Re-run the template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 5: Run a focused regression**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: merge event stream into chat panel"
```

### Task 5: Final verification

**Files:**
- No code changes expected

**Step 1: Run the focused test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 2: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS with `0` failures and `0` errors.

**Step 3: Manual smoke check**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn spring-boot:run
```

Then verify:

- the page opens without the old hero section
- the left editor can be typed into and saved
- the right panel shows the submitted instruction and streamed events in one feed
- task completion updates the editor and diff panel
- knowledge upload still appears and remains usable

**Step 4: Commit if manual-only fixes were needed**

```bash
git add <changed-files>
git commit -m "fix: polish editor chat workbench"
```

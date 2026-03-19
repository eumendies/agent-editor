# Knowledge Upload UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal knowledge-base upload panel to the demo page so users can upload a file and see a single success or error result without leaving the existing demo UI.

**Architecture:** Keep the feature entirely inside the existing Thymeleaf demo template. Reuse the existing `KnowledgeBaseController` upload endpoint, add a small form and result area near the bottom of `index.html`, and extend the current page script with one upload handler and a small amount of UI state.

**Tech Stack:** Thymeleaf template HTML, inline CSS, inline browser JavaScript, Spring Boot controller endpoint, JUnit 5 template test

---

### Task 1: Extend the demo page template test for the knowledge upload panel

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Add assertions for the new panel title and core DOM ids:

```java
assertTrue(template.contains("Knowledge Base Upload"));
assertTrue(template.contains("knowledgeUploadForm"));
assertTrue(template.contains("knowledgeFileInput"));
assertTrue(template.contains("knowledgeCategoryInput"));
assertTrue(template.contains("knowledgeUploadResult"));
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: FAIL because the template does not yet contain the upload UI.

**Step 3: Commit nothing yet**

Do not commit until the matching template work is done.

### Task 2: Add the upload panel markup and styling

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Add the panel markup**

Add a new bottom section after the existing main stage with:

- a panel title `Knowledge Base Upload`
- a `form` with id `knowledgeUploadForm`
- a file input with id `knowledgeFileInput`
- a text input with id `knowledgeCategoryInput`
- a submit button with id `knowledgeUploadBtn`
- a result container with id `knowledgeUploadResult`

Use the existing panel vocabulary and keep the structure compact.

**Step 2: Add minimal styles**

Add CSS for:

- text input and file input to match current controls
- a two-column grid on desktop and one-column layout on mobile
- result cards for success and error states

Do not redesign the rest of the page.

**Step 3: Run the template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: add knowledge upload panel markup"
```

### Task 3: Add the upload request flow and result rendering

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Add a small JavaScript upload handler**

Inside the existing page script:

- register a submit handler for `knowledgeUploadForm`
- validate file and category
- send `multipart/form-data` to `/api/v1/knowledge/documents`
- disable `knowledgeUploadBtn` while uploading
- render success or error output into `knowledgeUploadResult`

Minimal structure:

```javascript
async function uploadKnowledgeDocument(event) {
    event.preventDefault();
    // validate
    // build FormData
    // fetch POST /api/v1/knowledge/documents
    // render success/error
}
```

Add small helper render functions if needed, but keep the script local to this page.

**Step 2: Render the result card**

Success card should show:

- file name
- category
- status
- document id

Error state should show:

- short error title
- readable error message

**Step 3: Re-run the template test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 4: Run a focused backend/frontend regression**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest,KnowledgeBaseControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: wire knowledge upload ui"
```

### Task 4: Final verification

**Files:**
- No code changes expected

**Step 1: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS with `0` failures and `0` errors.

**Step 2: Manual smoke check**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn spring-boot:run
```

Then verify in the browser:

- the knowledge upload panel appears at the bottom of the demo page
- selecting a file and entering category sends the request
- success response renders a result card

**Step 3: Commit if manual-only tweaks were needed**

```bash
git add <changed-files>
git commit -m "fix: polish knowledge upload ui"
```

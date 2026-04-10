# Figma Editor Page Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the Spring Boot index template with a Figma-style light AI document editor page while preserving backend integrations.

**Architecture:** Keep the page as one Thymeleaf template with native HTML, CSS, and JavaScript. Rebuild the markup and CSS around a top bar plus Flexbox two-column workspace, then reconnect the existing document, diff, chat, knowledge upload, WebSocket, and user profile behavior through stable element ids.

**Tech Stack:** HTML5, CSS3 Flexbox, vanilla JavaScript, Spring Boot Thymeleaf template tests with JUnit 5.

---

### Task 1: Template Contract Test

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Replace the old dark-workbench assertions with assertions for:

- `AI Document Editor UI Design`
- `figma-topbar`
- `app-workspace`
- `document-column`
- `assistant-panel`
- `knowledgeUploadForm`
- `knowledgeFileInput`
- `documentEditor`
- `diffView`
- `userProfilePanel`
- `chatComposer`
- `agentMode`
- `sendCurrentMessage`
- `display: flex;`
- Absence of `Document Workspace`, `Chat Workspace`, old dark radial gradients, and old panel border radius.

**Step 2: Run test to verify it fails**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: FAIL because `index.html` has the old dark workbench structure.

**Step 3: Commit the failing test**

Do not commit while red; continue to Task 2.

### Task 2: Rebuild `index.html`

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Write minimal implementation**

Rebuild the template with:

- top dark Figma-style bar
- Flexbox `app-workspace`
- left `document-column`
- right fixed-width `assistant-panel`
- light editor, diff, user profile, and chat styling
- existing ids and function names preserved or adapted

**Step 2: Run focused test**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: PASS.

**Step 3: Refactor**

Review CSS for project UI constraints:

- no framework classes
- no Tailwind/Bootstrap
- Flexbox for main split
- card/button radius at or below 8px
- no dark gradient workbench remnants
- stable mobile layout

**Step 4: Commit**

```bash
git add src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java src/main/resources/templates/index.html docs/plans/2026-04-10-figma-editor-page-design.md docs/plans/2026-04-10-figma-editor-page.md
git commit -m "feat: recreate editor page from figma design"
```

### Task 3: Verification

**Files:**
- Verify: `src/main/resources/templates/index.html`
- Verify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Run focused template test**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: PASS.

**Step 2: Run full test suite if practical**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS, unless unrelated environment dependencies block the suite.

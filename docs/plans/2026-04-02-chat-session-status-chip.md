# Chat Session Status Chip Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a color-coded status chip to each chat session header so running, completed, and failed sessions are easier to scan.

**Architecture:** Keep the change entirely inside the existing demo page template. Update the template test first, then add small CSS for chip variants and centralize status updates in one helper so the runtime state and visual class stay in sync.

**Tech Stack:** Thymeleaf template HTML, inline CSS, inline browser JavaScript, JUnit 5 template test

---

### Task 1: Add a failing template test for the status chip hooks

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Add assertions for:

- `chat-session-status`
- `setSessionStatus`
- `status-running`
- `status-completed`
- `status-failed`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: FAIL because the current template still renders plain status text.

### Task 2: Add status chip styling and runtime class updates

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Add chip styles**

Create a reusable `chat-session-status` pill with variant classes for:

- `status-running`
- `status-completed`
- `status-failed`

**Step 2: Add a central status helper**

Implement a `setSessionStatus` helper that:

- updates the status text
- resets and reapplies the correct status variant class

**Step 3: Wire lifecycle transitions**

Use the helper for:

- session creation → `Running`
- successful completion → `Completed`
- failed completion/submission → `Failed`

### Task 3: Verify

**Files:**
- No additional files expected

**Step 1: Run focused test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS

# Minimal Chat Workspace Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify the right-side chat workspace into a basic message list plus one composer with input, mode selector, and send button.

**Architecture:** Keep all changes inside the existing demo template. Update the template test first, then remove the grouped-session and trace UI, flatten message rendering back to a simple list, and route send actions through the selected mode dropdown.

**Tech Stack:** Thymeleaf template HTML, inline CSS, inline browser JavaScript, JUnit 5 template test

---

### Task 1: Add a failing template test for the minimal chat layout

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Assert:

- `chatComposer`
- `sendButton`
- `messageList`

And assert removed:

- `chat-session`
- `tracePanel`
- `clearChatBtn`
- `reactBtn`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: FAIL

### Task 2: Rewrite the chat workspace

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Simplify markup**

Replace the current right-side grouped session UI with:

- a compact top meta row
- a message list container
- a minimal composer containing textarea, dropdown, send button

**Step 2: Simplify styles**

Remove session-group CSS and unused trace/progress styling. Keep only message bubbles and a compact composer layout.

**Step 3: Simplify script state**

Remove grouped-session helpers and route `sendButton` through the selected `agentMode`.

### Task 3: Verify

**Step 1: Run focused test**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS

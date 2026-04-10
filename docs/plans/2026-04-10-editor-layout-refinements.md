# Editor Layout Refinements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refine the Figma-style editor page header, agent mode labels, split pane resizing, and composer textarea sizing.

**Architecture:** Keep the existing single-template vanilla implementation. Add one resize handle between the flex columns and a small JavaScript controller that updates the existing CSS custom property for right panel width.

**Tech Stack:** HTML5, CSS3 Flexbox, vanilla JavaScript, JUnit 5 template tests.

---

### Task 1: Update Template Contract Test

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write failing assertions**

Assert:

- `Agent Editor`
- `workspace-resizer`
- `initWorkspaceResizer`
- `autoResizeInstructionInput`
- `max-height: 140px;`
- `<option value="REACT">ReAct</option>`
- `<option value="PLANNING">Planning</option>`
- `<option value="REFLEXION">Reflexion</option>`
- `<option value="SUPERVISOR">Supervisor</option>`

Assert absence:

- `AI Document Editor UI Design`
- `brand-dot`
- `ai-badge`
- `share-button`
- `<option value="REACT">Editor</option>`

**Step 2: Run red test**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: FAIL because the current template still has old topbar elements and missing resize/autoresize functions.

### Task 2: Implement Template Refinements

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Header**

Replace the current topbar content with a centered `Agent Editor` title. Remove unused topbar icon classes from CSS.

**Step 2: Agent mode labels**

Change visible option labels to `ReAct`, `Planning`, `Reflexion`, `Supervisor`. Keep values unchanged.

**Step 3: Resizable split**

Insert `<div id="workspaceResizer" class="workspace-resizer" ...></div>` between columns.

Add `initWorkspaceResizer()`:

- skip when viewport is below 1100px
- on pointer drag, compute right panel width from viewport right edge to pointer
- clamp width to 320-560px and keep left side at least 520px
- write width to `document.documentElement.style.setProperty("--assistant-width", width + "px")`

**Step 4: Auto-resize composer**

Add `autoResizeInstructionInput()` and call it on:

- page load
- `input` event
- after send clears the textarea

**Step 5: Run focused test**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: PASS.

### Task 3: Verification and Commit

**Step 1: Run full tests**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS.

**Step 2: Commit**

```bash
git add docs/plans/2026-04-10-editor-layout-refinements-design.md docs/plans/2026-04-10-editor-layout-refinements.md src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java src/main/resources/templates/index.html
git commit -m "feat: refine editor split layout"
```

# Agent V2 Demo Page Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rework the existing demo page into an orchestration-focused dashboard that highlights ReAct, Planning, and Supervisor flows.

**Architecture:** Keep the existing Thymeleaf single-page template and backend endpoints. Replace the page structure, styling, and client-side rendering to emphasize mode comparison, event flow, and result visibility. Add one lightweight smoke test that locks the expected demo copy.

**Tech Stack:** Thymeleaf template, vanilla JavaScript, Spring Boot test, Java 17

---

### Task 1: Add a smoke test for the demo page copy

**Files:**
- Create: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`
- Read: `src/main/resources/templates/index.html`

**Step 1: Write the failing test**

Add a test that reads `src/main/resources/templates/index.html` and asserts it contains:

- `Agent V2 Orchestration Demo`
- `Run ReAct`
- `Run Planning`
- `Run Supervisor`

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DemoPageTemplateTest test
```

**Step 3: Implement minimal test file**

Use a JUnit 5 test with `Files.readString(Path.of(...))` and plain `assertTrue(...)`.

**Step 4: Re-run test**

Expected: fail because the current template still uses the old demo copy.

### Task 2: Rebuild the template as an orchestration dashboard

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Replace the current layout**

Implement:

- hero section
- scenario bar
- mode lens cards
- original/result/diff panels
- execution timeline panel

**Step 2: Update client-side labels and behavior**

Implement:

- mode-aware execute button labels
- default showcase instruction text
- improved step rendering for planning and supervisor events
- clearer empty/loading states

**Step 3: Re-run smoke test**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DemoPageTemplateTest test
```

Expected: pass.

### Task 3: Run full regression

**Files:**
- No file changes required unless regressions appear

**Step 1: Run full test suite**

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test
```

**Step 2: If green, commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java docs/plans/2026-03-14-agent-v2-demo-page-design.md docs/plans/2026-03-14-agent-v2-demo-page-implementation.md
git commit -m "feat: redesign agent demo page"
```

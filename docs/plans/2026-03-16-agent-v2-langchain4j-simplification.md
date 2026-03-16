# Agent V2 LangChain4j Simplification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify the high-priority `agent.v2` model and tool protocol code by introducing structured planning output, tighter structured supervisor routing, and typed document tool arguments without changing the custom runtime loop.

**Architecture:** Keep runtime, orchestration, tracing, and tool allow-list enforcement unchanged. Refactor only the planning model boundary, the supervisor routing contract, and the document tool argument decoding path so the system uses more of langchain4j's structured capabilities and less handwritten parsing logic.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, langchain4j 1.11.0, Jackson

---

### Task 1: Refactor Planning To Typed AI Output

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAiService.java`
- Create: `src/main/java/com/agent/editor/agent/v2/planning/PlanningResponse.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/config/PlanningAgentConfig.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentDefinitionTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- typed planning output is converted into `PlanResult`
- empty or invalid planning output falls back to a single-step plan

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PlanningAgentDefinitionTest test`

Expected: FAIL because the new typed service path does not exist yet.

**Step 3: Write minimal implementation**

- add `PlanningAiService`
- add a minimal typed planning response contract
- update `PlanningAgentDefinition` to use the service
- keep existing fallback behavior explicit

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PlanningAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/planning src/main/java/com/agent/editor/config/PlanningAgentConfig.java src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentDefinitionTest.java
git commit -m "refactor: use typed ai service for planning"
```

### Task 2: Tighten Supervisor Structured Routing

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorRoutingAiService.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorRoutingResponse.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing tests**

Add or update tests that prove:

- valid structured routing still returns `AssignWorker`
- invalid worker ids still fall back to rule-based selection
- completion output still maps to `SupervisorDecision.Complete`

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HybridSupervisorAgentDefinitionTest test`

Expected: FAIL because the tightened routing contract or revised prompt expectations are not implemented yet.

**Step 3: Write minimal implementation**

- reduce prompt-level schema duplication
- keep `SupervisorRoutingResponse` minimal and typed
- preserve current candidate validation and fallback behavior

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HybridSupervisorAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "refactor: tighten supervisor routing contract"
```

### Task 3: Introduce Typed Document Tool Arguments

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/EditDocumentArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/SearchContentArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/ToolArgumentDecoder.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/EditDocumentTool.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/SearchContentTool.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/EditDocumentToolTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/SearchContentToolTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- valid JSON arguments decode into typed objects and keep current tool behavior
- invalid JSON raises a clear exception
- missing required fields keep the expected behavior for each tool

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EditDocumentToolTest,SearchContentToolTest test`

Expected: FAIL because typed argument decoding is not implemented yet.

**Step 3: Write minimal implementation**

- add typed argument records
- add a shared decoder
- replace direct `JsonNode` access with typed field access

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=EditDocumentToolTest,SearchContentToolTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document src/test/java/com/agent/editor/agent/v2/tool/document/EditDocumentToolTest.java src/test/java/com/agent/editor/agent/v2/tool/document/SearchContentToolTest.java
git commit -m "refactor: type document tool arguments"
```

### Task 4: Full Verification

**Files:**
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/EditDocumentToolTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/document/SearchContentToolTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Run focused regression tests**

Run:

```bash
mvn -Dtest=PlanningAgentDefinitionTest,PlanningThenExecutionOrchestratorTest,HybridSupervisorAgentDefinitionTest,SupervisorOrchestratorTest,EditDocumentToolTest,SearchContentToolTest test
```

Expected: PASS

**Step 2: Run the full suite**

Run: `mvn test`

Expected: PASS

**Step 3: Commit**

```bash
git add docs/plans/2026-03-16-agent-v2-langchain4j-simplification-design.md docs/plans/2026-03-16-agent-v2-langchain4j-simplification.md
git commit -m "docs: plan langchain4j simplification refactor"
```

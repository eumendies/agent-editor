# Agent V2 React Memory And AiService Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce `AiService`-based React model integration and session-scoped `ChatMemory` for both React and Supervisor while preserving the existing custom runtime and orchestration behavior.

**Architecture:** Keep `DefaultExecutionRuntime`, `Decision`, tool execution, and supervisor orchestration unchanged. Refactor only the model boundary and configuration by adding a `ReactAiService`, a bounded memory provider, and memory-aware wiring for React and Supervisor.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, langchain4j 1.11.0, langchain4j chat memory

---

### Task 1: Add Memory-Aware LangChain Configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/agent/editor/config/LangChainConfig.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Add or update tests that prove:

- a bounded chat memory provider is available in configuration
- the application still wires `agent.v2` components correctly after adding memory support

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=AgentV2ConfigurationSplitTest test`

Expected: FAIL because memory-aware wiring is not implemented yet.

**Step 3: Write minimal implementation**

- add the chat memory dependency
- add a bounded session-scoped memory provider bean
- keep existing `ChatModel` bean behavior unchanged

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add pom.xml src/main/java/com/agent/editor/config/LangChainConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git commit -m "refactor: add agent v2 chat memory wiring"
```

### Task 2: Move React To AiService

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/react/ReactAiService.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add tests that prove:

- React decisions still produce `Decision.ToolCalls`
- React completions still produce `Decision.Complete`
- the new `AiService` path can map model output correctly

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ReactAgentDefinitionTest test`

Expected: FAIL because the `AiService` path does not exist yet.

**Step 3: Write minimal implementation**

- add `ReactAiService`
- update `ReactAgentDefinition` to call it
- preserve existing trace collection and decision mapping behavior

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ReactAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react src/main/java/com/agent/editor/config/ReactAgentConfig.java src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java
git commit -m "refactor: move react agent to ai service"
```

### Task 3: Add Session-Scoped Memory Keys For React And Supervisor

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java`
- Modify: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`

**Step 1: Write the failing tests**

Add tests that prove:

- standalone ReAct uses `sessionId`
- worker execution uses `sessionId:workerId`
- supervisor routing uses `sessionId:supervisor`
- supervisor fallback behavior is unchanged

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ReactAgentDefinitionTest,HybridSupervisorAgentDefinitionTest test`

Expected: FAIL because scoped memory keys are not implemented yet.

**Step 3: Write minimal implementation**

- derive explicit memory scope keys
- wire `ReactAiService` and `SupervisorRoutingAiService` through memory-aware builders
- keep worker memory isolated from supervisor memory

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ReactAgentDefinitionTest,HybridSupervisorAgentDefinitionTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java src/main/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinition.java src/main/java/com/agent/editor/config/ReactAgentConfig.java src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java
git commit -m "refactor: scope react and supervisor chat memory"
```

### Task 4: Regression Verification

**Files:**
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/HybridSupervisorAgentDefinitionTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Run focused regression tests**

Run:

```bash
mvn -Dtest=ReactAgentDefinitionTest,HybridSupervisorAgentDefinitionTest,SupervisorOrchestratorTest,SingleAgentOrchestratorTest,PlanningThenExecutionOrchestratorTest,AgentV2ConfigurationSplitTest test
```

Expected: PASS

**Step 2: Run the full suite**

Run: `mvn test`

Expected: PASS

**Step 3: Commit**

```bash
git add docs/plans/2026-03-16-agent-v2-react-memory-design.md docs/plans/2026-03-16-agent-v2-react-memory.md
git commit -m "docs: plan react ai service and memory refactor"
```

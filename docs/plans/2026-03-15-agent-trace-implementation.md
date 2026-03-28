# Agent Trace Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `agent.v2` 增加一套开发调试用的 trace 能力，支持查看每个 task 的 prompt、模型响应、工具调用、工具结果和编排决策。

**Architecture:** 新增独立 `trace` 模块，与现有 `ExecutionEvent` 并行存在。runtime、definition 和 orchestrator 在关键节点调用 `TraceCollector` 写入 `TraceRecord`，第一版落到内存 `TraceStore`，并通过 controller 提供查询接口，最后在 demo 页增加简单 trace 展示面板。

**Tech Stack:** Spring Boot, Java 17, LangChain4j, in-memory store, existing demo page template

---

### Task 1: Add Trace Core Model

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/trace/TraceCategory.java`
- Create: `src/main/java/com/agent/editor/agent/v2/trace/TraceRecord.java`
- Create: `src/main/java/com/agent/editor/agent/v2/trace/TraceCollector.java`
- Create: `src/main/java/com/agent/editor/agent/v2/trace/TraceStore.java`
- Create: `src/main/java/com/agent/editor/agent/v2/trace/InMemoryTraceStore.java`
- Create: `src/main/java/com/agent/editor/agent/v2/trace/DefaultTraceCollector.java`
- Test: `src/test/java/com/agent/editor/agent/v2/trace/DefaultTraceCollectorTest.java`

**Step 1: Write the failing test**

Write a test proving a trace record can be appended and later queried by `taskId`.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DefaultTraceCollectorTest test
```

Expected: FAIL because trace classes do not exist yet.

**Step 3: Write minimal implementation**

Implement:

- `TraceCategory` enum
- `TraceRecord` record
- `TraceStore` interface
- `InMemoryTraceStore`
- `TraceCollector`
- `DefaultTraceCollector`

Ensure `DefaultTraceCollector` can persist ordered records per `taskId`.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/trace src/test/java/com/agent/editor/agent/v2/trace/DefaultTraceCollectorTest.java
git commit -m "feat: add agent trace core model"
```

### Task 2: Capture Runtime Trace

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntime.java`
- Test: `src/test/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Extend runtime tests to assert:

- iteration start produces a `STATE_SNAPSHOT`
- tool execution produces `TOOL_INVOCATION`
- tool completion produces `TOOL_RESULT`

Use a recording `TraceCollector` or in-memory store.

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DefaultExecutionRuntimeTest test
```

Expected: FAIL because runtime does not emit trace records yet.

**Step 3: Write minimal implementation**

Inject `TraceCollector` into `DefaultExecutionRuntime`.

Record:

- `STATE_SNAPSHOT` at iteration start
- `TOOL_INVOCATION` before `handler.execute(...)`
- `TOOL_RESULT` after tool returns

Payload should include:

- current content
- tool name
- arguments
- result message
- updated content

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntime.java src/test/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntimeTest.java
git commit -m "feat: capture runtime trace events"
```

### Task 3: Capture ReAct Model Trace

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/definition/ReactAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/definition/ReactAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add tests proving the definition emits:

- `MODEL_REQUEST` containing system prompt, user prompt and tool specifications
- `MODEL_RESPONSE` containing raw text and tool call list

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=ReactAgentDefinitionTest test
```

Expected: FAIL because no trace is emitted.

**Step 3: Write minimal implementation**

Inject `TraceCollector` into `ReactAgentDefinition`.

Capture:

- prompt payload before `chatModel.chat(...)`
- raw AI output and tool calls after response

Do not change existing toolLoopDecision semantics while adding trace.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/definition/ReactAgentDefinition.java src/test/java/com/agent/editor/agent/v2/definition/ReactAgentDefinitionTest.java
git commit -m "feat: capture react model trace"
```

### Task 4: Capture Planning And Supervisor Orchestration Trace

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/orchestration/SupervisorOrchestrator.java`
- Test: `src/test/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestratorTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/orchestration/SupervisorOrchestratorTest.java`

**Step 1: Write the failing test**

Add tests proving:

- planning writes plan-level `ORCHESTRATION_DECISION`
- supervisor writes worker assignment and summary `ORCHESTRATION_DECISION`

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=PlanningThenExecutionOrchestratorTest,SupervisorOrchestratorTest test
```

Expected: FAIL because orchestration trace is not emitted.

**Step 3: Write minimal implementation**

Inject `TraceCollector` into both orchestrators and record:

- plan text
- current plan step
- selected worker
- worker instruction
- worker summary
- final supervisor summary

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestrator.java src/main/java/com/agent/editor/agent/v2/orchestration/SupervisorOrchestrator.java src/test/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestratorTest.java src/test/java/com/agent/editor/agent/v2/orchestration/SupervisorOrchestratorTest.java
git commit -m "feat: capture orchestration trace"
```

### Task 5: Wire Trace Collector In Configuration

**Files:**
- Modify: `src/main/java/com/agent/editor/config/AgentV2Config.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigTest.java`

**Step 1: Write the failing test**

Write a configuration-level test proving:

- a `TraceStore` bean exists
- a `TraceCollector` bean exists
- runtime and react definition receive the collector

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=AgentV2ConfigTest test
```

Expected: FAIL because beans are not defined.

**Step 3: Write minimal implementation**

Update `AgentV2Config` to build and wire:

- `InMemoryTraceStore`
- `DefaultTraceCollector`
- runtime/definitions/orchestrators with collector dependency

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/AgentV2Config.java src/test/java/com/agent/editor/config/AgentV2ConfigTest.java
git commit -m "feat: wire trace collector into agent v2"
```

### Task 6: Add Trace Query API

**Files:**
- Create: `src/main/java/com/agent/editor/controller/TraceController.java`
- Modify: `src/main/java/com/agent/editor/service/TaskQueryService.java`
- Create: `src/main/java/com/agent/editor/dto/TraceSummaryResponse.java`
- Test: `src/test/java/com/agent/editor/controller/TraceControllerTest.java`
- Test: `src/test/java/com/agent/editor/service/TaskQueryServiceTest.java`

**Step 1: Write the failing test**

Add tests for:

- `GET /api/v1/agent/task/{taskId}/trace`
- `GET /api/v1/agent/task/{taskId}/trace/summary`

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=TraceControllerTest,TaskQueryServiceTest test
```

Expected: FAIL because endpoint and summary response do not exist.

**Step 3: Write minimal implementation**

Implement:

- trace list query by `taskId`
- summary aggregation grouped by category/iteration

Avoid complex filtering in the first version.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/TraceController.java src/main/java/com/agent/editor/service/TaskQueryService.java src/main/java/com/agent/editor/dto/TraceSummaryResponse.java src/test/java/com/agent/editor/controller/TraceControllerTest.java src/test/java/com/agent/editor/service/TaskQueryServiceTest.java
git commit -m "feat: expose agent trace query api"
```

### Task 7: Add Demo Trace Inspector

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Test: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing test**

Extend demo-page smoke test to assert presence of:

- `Trace Inspector`
- trace panel container
- trace fetch trigger

**Step 2: Run test to verify it fails**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=DemoPageTemplateTest test
```

Expected: FAIL because the demo page does not show trace yet.

**Step 3: Write minimal implementation**

Update demo page to:

- fetch `/trace` and `/trace/summary`
- render a compact trace inspector
- display prompt, tool calls and tool results in readable blocks

Keep the existing orchestration demo layout intact.

**Step 4: Run test to verify it passes**

Run the same command and expect PASS.

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: add trace inspector to demo page"
```

### Task 8: Run Full Verification

**Files:**
- No code changes required unless verification fails

**Step 1: Run full test suite**

Run:
```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn test
```

Expected: PASS with all tests green.

**Step 2: Manually verify trace flow**

Run the app locally and confirm:

- execute a `REACT` task
- query `/api/v1/agent/task/{taskId}/trace`
- confirm prompt/tool/result records exist
- execute `PLANNING` and `SUPERVISOR` tasks
- confirm orchestration trace exists

**Step 3: Commit final verification-only changes if needed**

If no code changes were required, skip commit.

If any verification fixes were needed:

```bash
git add <changed-files>
git commit -m "fix: finalize agent trace observability"
```

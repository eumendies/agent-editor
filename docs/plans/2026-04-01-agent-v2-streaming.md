# Agent V2 Streaming Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stream all frontend-visible `agent.v2` model text to the client in real time while deferring tool execution until a complete model response has been received.

**Architecture:** Add a shared streaming model-invocation adapter on top of LangChain4j `StreamingChatModel`, extend the native `ExecutionEvent` contract with text-stream events, route tool-loop agents through the adapter so they emit text deltas during generation, and keep `ToolLoopExecutionRuntime` responsible for actual tool execution after the final response is assembled.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Jackson, WebSocket, JUnit 5, Mockito, plain JavaScript

---

### Task 1: Verify And Wire Streaming Model Dependencies

**Files:**
- Modify: `src/main/java/com/agent/editor/config/LangChainConfig.java`
- Modify: `pom.xml` if LangChain4j upgrade is required
- Create: `src/test/java/com/agent/editor/config/LangChainConfigStreamingTest.java`
- Test: `src/test/java/com/agent/editor/config/LangChainConfigStreamingTest.java`

**Step 1: Write the failing test**

Add a focused Spring-free unit test that proves `LangChainConfig` exposes both:

- the existing blocking `ChatModel`
- a new `StreamingChatModel`

If a version upgrade is needed, also add a package-structure or reflective test that asserts the required streaming types are available at compile time.

Example target:

```java
assertNotNull(config.chatLanguageModel());
assertNotNull(config.streamingChatLanguageModel());
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LangChainConfigStreamingTest test`

Expected: FAIL because no streaming model bean/factory method exists yet, or because the dependency version lacks the required streaming API.

**Step 3: Write minimal implementation**

- add a `StreamingChatModel` bean built from the same API key/base URL/model configuration
- if the local LangChain4j version lacks the required streaming callbacks, upgrade only as far as needed and keep the rest of the project compiling
- keep the blocking `ChatModel` bean in place for any code not yet migrated

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LangChainConfigStreamingTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add pom.xml \
        src/main/java/com/agent/editor/config/LangChainConfig.java \
        src/test/java/com/agent/editor/config/LangChainConfigStreamingTest.java
git commit -m "feat: add langchain streaming model wiring"
```

### Task 2: Add Native Text Streaming Events And Transport Tests

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/event/EventType.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/event/ExecutionEvent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/event/WebSocketEventPublisher.java`
- Modify: `src/main/java/com/agent/editor/service/TaskQueryService.java`
- Modify: `src/main/java/com/agent/editor/websocket/WebSocketService.java`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskQueryEventTest.java`
- Test: `src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java`
- Test: `src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java`
- Test: `src/test/java/com/agent/editor/service/TaskQueryEventTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- `EventType` includes `TEXT_STREAM_STARTED`, `TEXT_STREAM_DELTA`, and `TEXT_STREAM_COMPLETED`
- text-stream events are stored and replayed through `TaskQueryService`
- `WebSocketEventPublisher` and `WebSocketService` forward those events to the v2 stream unchanged

For the frontend template, define a contract grep check that the page handles text-stream event types:

Run: `rg -n "TEXT_STREAM_STARTED|TEXT_STREAM_DELTA|TEXT_STREAM_COMPLETED" src/main/resources/templates/index.html`

Expected before implementation: no matches

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskQueryEventTest,WebSocketEventPublisherTest,WebSocketServiceV2Test test`

Expected: FAIL because the new event types are not defined or not exercised.

**Step 3: Write minimal implementation**

- extend `EventType`
- keep `ExecutionEvent` backward-compatible; only add fields if the frontend truly needs stream-turn metadata
- preserve existing event storage/publish behavior so text stream events ride the same path as task and tool events
- update the demo page event renderer to accumulate streaming text deltas into the current visible assistant turn

**Step 4: Run tests and contract check to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskQueryEventTest,WebSocketEventPublisherTest,WebSocketServiceV2Test test`

Expected: PASS

Run: `rg -n "TEXT_STREAM_STARTED|TEXT_STREAM_DELTA|TEXT_STREAM_COMPLETED" src/main/resources/templates/index.html`

Expected: matches present

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/event/EventType.java \
        src/main/java/com/agent/editor/agent/v2/event/ExecutionEvent.java \
        src/main/java/com/agent/editor/agent/v2/event/WebSocketEventPublisher.java \
        src/main/java/com/agent/editor/service/TaskQueryService.java \
        src/main/java/com/agent/editor/websocket/WebSocketService.java \
        src/main/resources/templates/index.html \
        src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java \
        src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java \
        src/test/java/com/agent/editor/service/TaskQueryEventTest.java
git commit -m "feat: add native text streaming events"
```

### Task 3: Introduce A Shared Streaming Decision Invoker

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/model/StreamingDecisionInvoker.java`
- Create: `src/main/java/com/agent/editor/agent/v2/model/StreamingInvocationResult.java`
- Create: `src/test/java/com/agent/editor/agent/v2/model/StreamingDecisionInvokerTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/model/StreamingDecisionInvokerTest.java`

**Step 1: Write the failing tests**

Add tests proving the new invoker:

- publishes `TEXT_STREAM_STARTED` before the first delta
- publishes one or more `TEXT_STREAM_DELTA` events while the model streams text
- buffers complete tool calls without executing anything
- returns an aggregated result containing final text and complete tool-call requests only after stream completion

Use a fake `StreamingChatModel` in the test rather than mocking callback ordering loosely.

Suggested assertions:

```java
assertEquals(List.of(
        EventType.TEXT_STREAM_STARTED,
        EventType.TEXT_STREAM_DELTA,
        EventType.TEXT_STREAM_DELTA,
        EventType.TEXT_STREAM_COMPLETED
), publishedTypes);
assertEquals(1, result.getToolExecutionRequests().size());
assertEquals("hello world", result.getText());
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StreamingDecisionInvokerTest test`

Expected: FAIL because the invoker classes do not exist yet.

**Step 3: Write minimal implementation**

- create a small adapter around `StreamingChatModel`
- use a completion primitive such as `CompletableFuture` to bridge callback-style streaming into the current synchronous runtime flow
- publish stream events from inside the adapter
- aggregate final text in order
- expose completed tool calls as `ToolExecutionRequest`-compatible data or a project-local equivalent
- add concise Chinese comments around the callback sequencing invariants

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StreamingDecisionInvokerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/model/StreamingDecisionInvoker.java \
        src/main/java/com/agent/editor/agent/v2/model/StreamingInvocationResult.java \
        src/test/java/com/agent/editor/agent/v2/model/StreamingDecisionInvokerTest.java
git commit -m "feat: add streaming decision invoker"
```

### Task 4: Route Tool-Loop Agents Through The Streaming Invoker

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritic.java`
- Modify: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ReflexionAgentConfig.java`
- Modify: relevant existing agent tests under `src/test/java/com/agent/editor/agent/v2/**`
- Test: focused existing agent tests plus any new ones needed

**Step 1: Write the failing tests**

For each agent family, add or update tests proving:

- plain text responses still end as `ToolLoopDecision.Complete`
- complete tool-call responses end as `ToolLoopDecision.ToolCalls`
- the agent no longer depends on a blocking `ChatModel` when streaming is enabled
- structured-output agents still parse aggregated text after stream completion

At minimum cover:

- `ReactAgentTest`
- `ResearcherAgentTest`
- `GroundedWriterAgentTest`
- `EvidenceReviewerAgentTest`
- `ReflexionCriticTest`

**Step 2: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,ReflexionCriticTest test`

Expected: FAIL because constructors/configuration still require blocking-only model invocation.

**Step 3: Write minimal implementation**

- inject `StreamingDecisionInvoker` or `StreamingChatModel`-backed collaborator into each migrated agent
- keep current initial deterministic retrieval behavior in `ResearcherAgent`
- preserve existing parsing and fallback semantics after the final aggregated text is available
- do not move tool execution into the agent layer

**Step 4: Run focused tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,ReflexionCriticTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgent.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritic.java \
        src/main/java/com/agent/editor/config/ReactAgentConfig.java \
        src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/main/java/com/agent/editor/config/ReflexionAgentConfig.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticTest.java
git commit -m "feat: stream tool-loop agent model responses"
```

### Task 5: Preserve Runtime Tool Semantics While Integrating Streamed Turns

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/memory/ChatMessage.java` only if additional streamed-text memory representation is required
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- runtime still emits `TOOL_CALLED` only after the model response has fully completed
- streamed text does not cause premature tool execution
- final AI/tool-call memory entries remain consistent with existing transcript semantics

If runtime changes are unnecessary after the agent migration, let the tests document that the old behavior remains intact with streamed decisions.

**Step 2: Run test to verify it fails or to confirm the current gap**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest test`

Expected: FAIL if runtime assumptions need adjustment, or expose missing coverage for the new streamed-decision path.

**Step 3: Write minimal implementation**

- keep `ToolLoopExecutionRuntime` consuming complete `ToolLoopDecision` objects
- only adjust runtime code if needed to record or order streamed events correctly
- keep Chinese comments focused on the “text first, tools after complete response” invariant

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java \
        src/main/java/com/agent/editor/agent/v2/core/memory/ChatMessage.java \
        src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java
git commit -m "test: lock runtime tool ordering for streamed responses"
```

### Task 6: Finish Frontend Integration And Run Full Verification

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: any directly related controller/service tests if event payload usage changes
- Test: focused manual and automated verification

**Step 1: Add final focused checks**

Before the last code edit, search for the remaining assumptions that one agent text response arrives as one event:

Run: `rg -n "TASK_COMPLETED|event.message|currentResponse|assistant|stream" src/main/resources/templates/index.html`

Capture where the page still assumes one-shot assistant text rendering.

**Step 2: Write minimal implementation**

- keep one active streamed assistant turn per task execution step
- append `TEXT_STREAM_DELTA` chunks live
- finalize the turn on `TEXT_STREAM_COMPLETED`
- continue rendering tool and task events separately

**Step 3: Run targeted regression suites**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LangChainConfigStreamingTest,StreamingDecisionInvokerTest,ReactAgentTest,ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,ReflexionCriticTest,ToolLoopExecutionRuntimeTest,TaskQueryEventTest,WebSocketEventPublisherTest,WebSocketServiceV2Test test`

Expected: PASS

**Step 4: Run full project verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

Manual verification:

- run `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn spring-boot:run`
- open the demo page
- execute one REACT task that only returns text
- execute one REACT or SUPERVISOR task that first emits text and then triggers a tool
- confirm text appears incrementally and `TOOL_CALLED` only appears after the model turn finishes

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html \
        src/test/java/com/agent/editor/**/*.java
git add src/main/java/com/agent/editor/**/*.java
git commit -m "feat: stream agent v2 model text before tool execution"
```

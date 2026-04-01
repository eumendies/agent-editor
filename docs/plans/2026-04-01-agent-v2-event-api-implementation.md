# Agent V2 Event API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add native `/api/v2/agent/**` and `/ws/agent/v2` endpoints, migrate the demo page to native v2 events, and remove `LegacyEventAdapter` from production wiring while keeping existing v1 compatibility endpoints available.

**Architecture:** Reuse the current v2 task orchestration pipeline and `TaskQueryService` as the single source of `ExecutionEvent` history, add a parallel v2 HTTP/WebSocket protocol surface that exposes native event data, then cut the page over to that surface and delete legacy event projection from the v2 runtime path.

**Tech Stack:** Java 17, Spring Boot, Lombok, Jackson, WebSocket, JUnit 5, Mockito, plain JavaScript

---

### Task 1: Expose Native Task/Event Queries Through Application Service And V2 Controller

**Files:**
- Create: `src/main/java/com/agent/editor/controller/AgentV2Controller.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/service/TaskQueryService.java`
- Create: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskQueryEventTest.java`
- Test: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Test: `src/test/java/com/agent/editor/service/TaskQueryEventTest.java`

**Step 1: Write the failing tests**

Add tests proving:

- `AgentV2Controller` delegates `POST /execute` and `GET /task/{taskId}` to `TaskApplicationService`
- `GET /api/v2/agent/task/{taskId}/events` returns native `ExecutionEvent` objects
- `TaskQueryService` exposes stored events directly without projecting them into `AgentStep`

Example assertion target:

```java
assertEquals(EventType.TOOL_CALLED, result.getBody().get(0).getType());
assertEquals("editDocument", result.getBody().get(0).getMessage());
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,TaskQueryEventTest test`

Expected: FAIL because the v2 controller and event-query application methods do not exist yet.

**Step 3: Write minimal implementation**

- add `AgentV2Controller` under `/api/v2/agent`
- keep `AgentTaskRequest` / `AgentTaskResponse` reuse unless a field mismatch forces a new DTO
- add `TaskApplicationService#getTaskEvents(String taskId)`
- keep `TaskQueryService#getEvents(String taskId)` as the source of truth and stop adding new v2 code that depends on `getTaskSteps(...)`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,TaskQueryEventTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller/AgentV2Controller.java \
        src/main/java/com/agent/editor/service/TaskApplicationService.java \
        src/main/java/com/agent/editor/service/TaskQueryService.java \
        src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java \
        src/test/java/com/agent/editor/service/TaskQueryEventTest.java
git commit -m "feat: add native agent v2 task event api"
```

### Task 2: Add A Native V2 WebSocket Stream For Execution Events

**Files:**
- Create: `src/main/java/com/agent/editor/dto/AgentEventStreamMessage.java`
- Create: `src/main/java/com/agent/editor/websocket/AgentV2WebSocketHandler.java`
- Modify: `src/main/java/com/agent/editor/websocket/WebSocketService.java`
- Modify: `src/main/java/com/agent/editor/config/WebSocketConfig.java`
- Create: `src/test/java/com/agent/editor/websocket/AgentV2WebSocketHandlerTest.java`
- Create: `src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java`
- Test: `src/test/java/com/agent/editor/websocket/AgentV2WebSocketHandlerTest.java`
- Test: `src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java`

**Step 1: Write the failing tests**

Add tests proving:

- `/ws/agent/v2` sends a connection message carrying `sessionId`
- the websocket service can bind a v2 session to a task and deliver a native event payload without legacy `stepType`
- v1 websocket behavior remains untouched

Suggested envelope:

```java
new AgentEventStreamMessage("CONNECTED", null, sessionId)
new AgentEventStreamMessage("EVENT", executionEvent, null)
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2WebSocketHandlerTest,WebSocketServiceV2Test test`

Expected: FAIL because the v2 websocket handler/message type does not exist yet.

**Step 3: Write minimal implementation**

- add a v2 websocket handler at `/ws/agent/v2`
- introduce a small v2 stream envelope DTO for connection bootstrap plus event delivery
- extend `WebSocketService` with explicit v2 session registration / task binding / event send methods, or equivalent clearly separated methods
- keep the existing v1 `WebSocketMessage` methods intact

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2WebSocketHandlerTest,WebSocketServiceV2Test test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/dto/AgentEventStreamMessage.java \
        src/main/java/com/agent/editor/websocket/AgentV2WebSocketHandler.java \
        src/main/java/com/agent/editor/websocket/WebSocketService.java \
        src/main/java/com/agent/editor/config/WebSocketConfig.java \
        src/test/java/com/agent/editor/websocket/AgentV2WebSocketHandlerTest.java \
        src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java
git commit -m "feat: add native agent v2 websocket stream"
```

### Task 3: Move The V2 Runtime Publisher To Native Events And Delete LegacyEventAdapter

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/event/WebSocketEventPublisher.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/event/LegacyEventAdapter.java`
- Delete: `src/test/java/com/agent/editor/agent/v2/event/LegacyEventAdapterTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing tests**

Update tests to prove:

- `WebSocketEventPublisher` persists `ExecutionEvent` and forwards it to the v2 websocket path
- Spring configuration no longer creates a `LegacyEventAdapter` bean
- published websocket payload contains the native event body

Example matcher target:

```java
argThat(message ->
        "EVENT".equals(message.getType())
                && message.getEvent().getType() == EventType.TOOL_CALLED)
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=WebSocketEventPublisherTest,AgentV2ConfigurationSplitTest test`

Expected: FAIL because the publisher still depends on `LegacyEventAdapter`.

**Step 3: Write minimal implementation**

- change `WebSocketEventPublisher` to send native events through the new v2 websocket path
- remove `LegacyEventAdapter` bean wiring from `TaskOrchestratorConfig`
- delete the adapter class and its direct tests
- leave v1 controller/websocket compatibility code untouched

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=WebSocketEventPublisherTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/event/WebSocketEventPublisher.java \
        src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java \
        src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git rm src/main/java/com/agent/editor/agent/v2/event/LegacyEventAdapter.java \
       src/test/java/com/agent/editor/agent/v2/event/LegacyEventAdapterTest.java
git commit -m "refactor: remove legacy event adapter from agent v2 runtime"
```

### Task 4: Migrate The Demo Page To V2 HTTP And WebSocket Event Contracts

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Test: manual browser verification against the running app

**Step 1: Write the failing test**

Because the page is plain template JavaScript, define the failure as an observable contract check:

- the page must stop calling `/api/v1/agent/execute`
- the page must stop connecting to `/ws/agent`
- the page must render timeline items from `ExecutionEvent.type` / `ExecutionEvent.message`

Before editing, confirm the old strings still exist with:

Run: `rg -n "/api/v1/agent/execute|/ws/agent|stepType|message.type === \\\"STEP\\\"|case \\\"COMPLETED\\\"|case \\\"ERROR\\\"" src/main/resources/templates/index.html`

Expected: matches present

**Step 2: Write minimal implementation**

- switch execute requests to `/api/v2/agent/execute`
- switch replay requests to `/api/v2/agent/task/${taskId}/events`
- switch realtime connection to `/ws/agent/v2`
- update websocket handling to process:
  - connection envelopes
  - native event envelopes
- map `EventType` values into the existing timeline copy and styling rules
- keep trace/document/diff/knowledge requests unchanged

**Step 3: Run contract check to verify the old agent protocol is gone from the page**

Run: `rg -n "/api/v1/agent/execute|/ws/agent|stepType|message.type === \\\"STEP\\\"|case \\\"COMPLETED\\\"|case \\\"ERROR\\\"" src/main/resources/templates/index.html`

Expected: no matches for the migrated agent protocol strings

**Step 4: Run focused frontend verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,WebSocketEventPublisherTest,TaskApplicationServiceTest test`

Expected: PASS

Manual verification:

- run `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn spring-boot:run`
- open the demo page
- execute at least one REACT task and one SUPERVISOR task
- confirm the timeline updates live and still renders completion/failure correctly

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: migrate demo page to agent v2 event apis"
```

### Task 5: Run Full Regression Verification And Clean Up Remaining Legacy References

**Files:**
- Modify if needed: any remaining production/test files found by search

**Step 1: Search for stale references**

Run: `rg -n "LegacyEventAdapter|/api/v1/agent/execute|/ws/agent\\b|getTaskSteps\\(" src/main/java src/main/resources src/test/java`

Expected:

- no production references to `LegacyEventAdapter`
- no demo page references to old agent execute/websocket paths
- only deliberate v1 compatibility code remains

**Step 2: Run targeted regression suites**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentControllerTest,AgentV2ControllerTest,TaskQueryEventTest,TaskApplicationServiceTest,WebSocketEventPublisherTest,AgentV2WebSocketHandlerTest,WebSocketServiceV2Test,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 3: Run the full test suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: finish native agent v2 event migration"
```

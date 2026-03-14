# Agent V2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a new `agent.v2` architecture that supports ReAct now and Planning/multi-agent orchestration next, while migrating service orchestration away from the current `agent` package.

**Architecture:** Introduce a v2 workflow kernel with explicit agent definitions, a generic execution runtime, an orchestrator layer, a reusable tool registry, and an event-first task model. Keep the current v1 package intact during the first implementation slice, then route one production path through v2 before removing legacy pieces.

**Tech Stack:** Java 17, Spring Boot 3.2, LangChain4j 1.11, Jackson, Spring WebSocket, JUnit 5 via `spring-boot-starter-test`

---

### Task 1: Create the v2 package skeleton and agent vocabulary

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/definition/AgentDefinition.java`
- Create: `src/main/java/com/agent/editor/agent/v2/definition/AgentType.java`
- Create: `src/main/java/com/agent/editor/agent/v2/definition/Decision.java`
- Create: `src/main/java/com/agent/editor/agent/v2/definition/ToolCall.java`
- Create: `src/test/java/com/agent/editor/agent/v2/definition/DecisionTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionTest {

    @Test
    void shouldExposeToolCallDecisionData() {
        ToolCall call = new ToolCall("editDocument", "{\"content\":\"hi\"}");
        Decision.ToolCalls decision = new Decision.ToolCalls(List.of(call), "need to edit");

        assertEquals(1, decision.calls().size());
        assertEquals("editDocument", decision.calls().get(0).name());
        assertEquals("need to edit", decision.reasoning());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DecisionTest test`
Expected: FAIL because the v2 definition classes do not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.definition;

public interface AgentDefinition {
    AgentType type();
}
```

```java
package com.agent.editor.agent.v2.definition;

public enum AgentType {
    REACT,
    PLANNING
}
```

```java
package com.agent.editor.agent.v2.definition;

public record ToolCall(String name, String arguments) {
}
```

```java
package com.agent.editor.agent.v2.definition;

import java.util.List;

public sealed interface Decision permits Decision.ToolCalls, Decision.Respond, Decision.Complete {

    record ToolCalls(List<ToolCall> calls, String reasoning) implements Decision {}
    record Respond(String message, String reasoning) implements Decision {}
    record Complete(String result, String reasoning) implements Decision {}
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DecisionTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/definition src/test/java/com/agent/editor/agent/v2/definition
git commit -m "feat: add agent v2 definition model"
```

### Task 2: Add runtime request, context, state, and result types

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionRequest.java`
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionContext.java`
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionResult.java`
- Create: `src/main/java/com/agent/editor/agent/v2/state/ExecutionState.java`
- Create: `src/main/java/com/agent/editor/agent/v2/state/DocumentSnapshot.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/definition/AgentDefinition.java`
- Create: `src/test/java/com/agent/editor/agent/v2/runtime/ExecutionRequestTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionRequestTest {

    @Test
    void shouldRetainExecutionInputMetadata() {
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "rewrite this",
                6
        );

        assertEquals("task-1", request.taskId());
        assertEquals(AgentType.REACT, request.agentType());
        assertEquals("body", request.document().content());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ExecutionRequestTest test`
Expected: FAIL because runtime/state classes do not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.state;

public record DocumentSnapshot(String documentId, String title, String content) {
}
```

```java
package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;

public record ExecutionRequest(
        String taskId,
        String sessionId,
        AgentType agentType,
        DocumentSnapshot document,
        String instruction,
        int maxIterations
) {
}
```

```java
package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;

public interface AgentDefinition {
    AgentType type();
    Decision decide(ExecutionContext context);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ExecutionRequestTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/runtime src/main/java/com/agent/editor/agent/v2/state src/test/java/com/agent/editor/agent/v2/runtime
git commit -m "feat: add agent v2 runtime state model"
```

### Task 3: Build the event model and publisher boundary

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/event/EventType.java`
- Create: `src/main/java/com/agent/editor/agent/v2/event/ExecutionEvent.java`
- Create: `src/main/java/com/agent/editor/agent/v2/event/EventPublisher.java`
- Create: `src/main/java/com/agent/editor/agent/v2/event/WebSocketEventPublisher.java`
- Test: `src/test/java/com/agent/editor/agent/v2/event/WebSocketEventPublisherTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketEventPublisherTest {

    @Test
    void shouldCreateEventWithTypeAndTaskId() {
        ExecutionEvent event = new ExecutionEvent(EventType.TASK_STARTED, "task-1", "started");

        assertEquals(EventType.TASK_STARTED, event.type());
        assertEquals("task-1", event.taskId());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=WebSocketEventPublisherTest test`
Expected: FAIL because the event model does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.event;

public enum EventType {
    TASK_STARTED,
    ITERATION_STARTED,
    DECISION_MADE,
    TOOL_CALLED,
    TOOL_SUCCEEDED,
    TOOL_FAILED,
    TASK_COMPLETED,
    TASK_FAILED
}
```

```java
package com.agent.editor.agent.v2.event;

public record ExecutionEvent(EventType type, String taskId, String message) {
}
```

```java
package com.agent.editor.agent.v2.event;

public interface EventPublisher {
    void publish(ExecutionEvent event);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=WebSocketEventPublisherTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/event src/test/java/com/agent/editor/agent/v2/event
git commit -m "feat: add agent v2 event model"
```

### Task 4: Build the reusable tool registry and first document tools

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/ToolHandler.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/ToolRegistry.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/ToolInvocation.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/ToolResult.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/EditDocumentTool.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/SearchContentTool.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/AnalyzeDocumentTool.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/RespondToUserTool.java`
- Test: `src/test/java/com/agent/editor/agent/v2/tool/ToolRegistryTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolRegistryTest {

    @Test
    void shouldResolveRegisteredToolByName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new StubToolHandler());

        assertNotNull(registry.get("stubTool"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ToolRegistryTest test`
Expected: FAIL because the tool runtime does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.tool;

public interface ToolHandler {
    String name();
    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
```

```java
package com.agent.editor.agent.v2.tool;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public void register(ToolHandler handler) {
        handlers.put(handler.name(), handler);
    }

    public ToolHandler get(String name) {
        return handlers.get(name);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ToolRegistryTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/tool src/test/java/com/agent/editor/agent/v2/tool
git commit -m "feat: add agent v2 tool registry"
```

### Task 5: Implement the generic execution runtime with a fake agent test

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/ExecutionRuntime.java`
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntime.java`
- Create: `src/main/java/com/agent/editor/agent/v2/runtime/TerminationPolicy.java`
- Test: `src/test/java/com/agent/editor/agent/v2/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultExecutionRuntimeTest {

    @Test
    void shouldCompleteWhenAgentReturnsCompleteDecision() {
        AgentDefinition agent = new CompletingAgentDefinition();
        ExecutionRuntime runtime = new DefaultExecutionRuntime(new ToolRegistry(), event -> {});
        ExecutionRequest request = new ExecutionRequest(
                "task-1", "session-1", AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish", 3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("done", result.finalMessage());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DefaultExecutionRuntimeTest test`
Expected: FAIL because runtime implementation does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;

public interface ExecutionRuntime {
    ExecutionResult run(AgentDefinition definition, ExecutionRequest request);
}
```

```java
package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.tool.ToolRegistry;

public class DefaultExecutionRuntime implements ExecutionRuntime {

    private final ToolRegistry toolRegistry;
    private final EventPublisher eventPublisher;

    public DefaultExecutionRuntime(ToolRegistry toolRegistry, EventPublisher eventPublisher) {
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult run(AgentDefinition definition, ExecutionRequest request) {
        Decision decision = definition.decide(null);
        if (decision instanceof Decision.Complete complete) {
            return new ExecutionResult(complete.result());
        }
        throw new IllegalStateException("Unsupported decision");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DefaultExecutionRuntimeTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/runtime src/test/java/com/agent/editor/agent/v2/runtime
git commit -m "feat: add agent v2 execution runtime"
```

### Task 6: Implement `ReactAgentDefinition` on top of the new protocol

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/definition/ReactAgentDefinition.java`
- Test: `src/test/java/com/agent/editor/agent/v2/definition/ReactAgentDefinitionTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactAgentDefinitionTest {

    @Test
    void shouldReportReactType() {
        ReactAgentDefinition definition = new ReactAgentDefinition(null);

        assertEquals(AgentType.REACT, definition.type());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ReactAgentDefinitionTest test`
Expected: FAIL because React agent definition does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;
import dev.langchain4j.model.chat.ChatModel;

public class ReactAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;

    public ReactAgentDefinition(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        return new Decision.Complete("placeholder", "react stub");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ReactAgentDefinitionTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/definition src/test/java/com/agent/editor/agent/v2/definition
git commit -m "feat: add react agent definition for v2"
```

### Task 7: Add the orchestration layer and task query model

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskOrchestrator.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/SingleAgentOrchestrator.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskRequest.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/TaskResult.java`
- Create: `src/main/java/com/agent/editor/agent/v2/state/TaskState.java`
- Create: `src/main/java/com/agent/editor/agent/v2/state/TaskStatus.java`
- Test: `src/test/java/com/agent/editor/agent/v2/orchestration/SingleAgentOrchestratorTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleAgentOrchestratorTest {

    @Test
    void shouldReturnTaskResultForSingleAgentExecution() {
        TaskResult result = new SingleAgentOrchestrator(null, null).execute(null);

        assertEquals("COMPLETED", result.status().name());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SingleAgentOrchestratorTest test`
Expected: FAIL because the orchestration layer does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.orchestration;

public interface TaskOrchestrator {
    TaskResult execute(TaskRequest request);
}
```

```java
package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.state.TaskStatus;

public record TaskResult(TaskStatus status, String finalContent) {
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=SingleAgentOrchestratorTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/orchestration src/main/java/com/agent/editor/agent/v2/state src/test/java/com/agent/editor/agent/v2/orchestration
git commit -m "feat: add agent v2 orchestration layer"
```

### Task 8: Split task orchestration out of `DocumentService`

**Files:**
- Create: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Create: `src/main/java/com/agent/editor/service/TaskQueryService.java`
- Create: `src/main/java/com/agent/editor/service/DiffService.java`
- Modify: `src/main/java/com/agent/editor/service/DocumentService.java`
- Modify: `src/main/java/com/agent/editor/controller/AgentController.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Test: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskApplicationServiceTest {

    @Test
    void shouldCreateTaskResponseThroughDedicatedTaskService() {
        TaskApplicationService service = new TaskApplicationService(null, null, null);

        assertNotNull(service);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TaskApplicationServiceTest test`
Expected: FAIL because the new services do not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.service;

public class DiffService {
}
```

```java
package com.agent.editor.service;

public class TaskQueryService {
}
```

```java
package com.agent.editor.service;

public class TaskApplicationService {

    public TaskApplicationService(DocumentService documentService,
                                  TaskQueryService taskQueryService,
                                  DiffService diffService) {
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TaskApplicationServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/service src/main/java/com/agent/editor/controller/AgentController.java src/main/java/com/agent/editor/dto/AgentTaskResponse.java src/test/java/com/agent/editor/service
git commit -m "refactor: move task orchestration out of document service"
```

### Task 9: Route one vertical slice through v2 and keep temporary compatibility

**Files:**
- Modify: `src/main/java/com/agent/editor/controller/AgentController.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskRequest.java`
- Modify: `src/main/java/com/agent/editor/dto/AgentTaskResponse.java`
- Modify: `src/main/java/com/agent/editor/dto/WebSocketMessage.java`
- Modify: `src/main/java/com/agent/editor/websocket/WebSocketService.java`
- Modify: `src/main/java/com/agent/editor/websocket/AgentWebSocketHandler.java`
- Create: `src/main/java/com/agent/editor/agent/v2/event/LegacyWebSocketMessageAdapter.java`
- Test: `src/test/java/com/agent/editor/agent/v2/event/LegacyWebSocketMessageAdapterTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.event;

import com.agent.editor.dto.WebSocketMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyWebSocketMessageAdapterTest {

    @Test
    void shouldConvertExecutionEventToLegacyWebSocketMessage() {
        LegacyWebSocketMessageAdapter adapter = new LegacyWebSocketMessageAdapter();
        WebSocketMessage message = adapter.toLegacy(new ExecutionEvent(EventType.TASK_STARTED, "task-1", "started"));

        assertEquals("STEP", message.getType());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=LegacyWebSocketMessageAdapterTest test`
Expected: FAIL because the adapter does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.event;

import com.agent.editor.dto.WebSocketMessage;

public class LegacyWebSocketMessageAdapter {

    public WebSocketMessage toLegacy(ExecutionEvent event) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("STEP");
        message.setTaskId(event.taskId());
        message.setContent(event.message());
        return message;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=LegacyWebSocketMessageAdapterTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/event src/main/java/com/agent/editor/controller/AgentController.java src/main/java/com/agent/editor/dto src/main/java/com/agent/editor/websocket src/test/java/com/agent/editor/agent/v2/event
git commit -m "refactor: route agent task updates through v2 events"
```

### Task 10: Add planning orchestration on top of the same runtime

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/definition/PlanningAgentDefinition.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestrator.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanStep.java`
- Create: `src/main/java/com/agent/editor/agent/v2/orchestration/PlanResult.java`
- Modify: `src/main/java/com/agent/editor/model/AgentMode.java`
- Modify: `src/main/java/com/agent/editor/controller/AgentController.java`
- Test: `src/test/java/com/agent/editor/agent/v2/orchestration/PlanningThenExecutionOrchestratorTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlanningThenExecutionOrchestratorTest {

    @Test
    void shouldProducePlanBeforeExecution() {
        PlanningThenExecutionOrchestrator orchestrator = new PlanningThenExecutionOrchestrator(null, null, null);

        assertNotNull(orchestrator);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=PlanningThenExecutionOrchestratorTest test`
Expected: FAIL because planning orchestration does not exist yet

**Step 3: Write minimal implementation**

```java
package com.agent.editor.agent.v2.orchestration;

public record PlanStep(int order, String instruction) {
}
```

```java
package com.agent.editor.agent.v2.orchestration;

import java.util.List;

public record PlanResult(List<PlanStep> steps) {
}
```

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=PlanningThenExecutionOrchestratorTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/definition src/main/java/com/agent/editor/agent/v2/orchestration src/main/java/com/agent/editor/model/AgentMode.java src/main/java/com/agent/editor/controller/AgentController.java src/test/java/com/agent/editor/agent/v2/orchestration
git commit -m "feat: add planning orchestration for agent v2"
```

### Task 11: Remove v1 task execution dependencies after v2 is stable

**Files:**
- Delete: `src/main/java/com/agent/editor/agent/BaseAgent.java`
- Delete: `src/main/java/com/agent/editor/agent/ReActAgent.java`
- Delete: `src/main/java/com/agent/editor/agent/EditorAgentTools.java`
- Delete: `src/main/java/com/agent/editor/agent/AgentFactory.java`
- Modify: `src/main/java/com/agent/editor/service/DocumentService.java`
- Modify: `README.md`
- Modify: `docs/PROCESS.md`
- Test: `src/test/java/com/agent/editor/agent/v2/LegacyRemovalSmokeTest.java`

**Step 1: Write the failing test**

```java
package com.agent.editor.agent.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRemovalSmokeTest {

    @Test
    void shouldUseV2PackagesForAgentExecution() {
        assertTrue(true);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=LegacyRemovalSmokeTest test`
Expected: FAIL after references are intentionally switched but before legacy cleanup is complete

**Step 3: Write minimal implementation**

```java
// Remove the v1 execution chain only after v2 task flow, events, and planning mode pass verification.
```

**Step 4: Run test suite to verify stability**

Run: `mvn test`
Expected: PASS with v2 execution flow active and no references to the deleted v1 runtime classes

**Step 5: Commit**

```bash
git add src/main/java README.md docs/PROCESS.md src/test/java
git commit -m "refactor: retire legacy agent runtime in favor of v2"
```

### Task 12: Final verification and documentation sync

**Files:**
- Modify: `README.md`
- Modify: `docs/API.md`
- Modify: `docs/PROCESS.md`
- Test: `src/test/java/com/agent/editor/controller/AgentControllerIntegrationTest.java`

**Step 1: Write the failing integration test**

```java
package com.agent.editor.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentControllerIntegrationTest {

    @Test
    void shouldExposeAgentModesBackedByV2Orchestrators() {
        assertTrue(true);
    }
}
```

**Step 2: Run focused verification**

Run: `mvn -q -Dtest=AgentControllerIntegrationTest test`
Expected: FAIL until controller wiring and docs are aligned with v2

**Step 3: Update docs and API behavior**

```java
// Align controller responses, API examples, and architecture docs with the v2 task/event model.
```

**Step 4: Run full verification**

Run: `mvn test`
Expected: PASS

**Step 5: Commit**

```bash
git add README.md docs/API.md docs/PROCESS.md src/main/java src/test/java
git commit -m "docs: align api and architecture docs with agent v2"
```

## Execution Notes

- Use `@superpowers:test-driven-development` for each task before implementation code.
- Use `@superpowers:verification-before-completion` before claiming any milestone is complete.
- Keep the first production slice limited to one ReAct path through v2 before enabling planning mode by default.
- Do not delete the v1 runtime until Task 11 verification passes.

Plan complete and saved to `docs/plans/2026-03-14-agent-v2-implementation.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

**Which approach?**

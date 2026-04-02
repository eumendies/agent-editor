# Agent V2 Async Submit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Convert native v2 agent execution into an async submit/background-run flow that returns `202 Accepted` and update the demo page to track progress until terminal completion.

**Architecture:** Keep `TaskOrchestrator` and runtime semantics unchanged, but move orchestration out of the servlet thread by splitting task submission from task execution inside `TaskApplicationService`. Use a dedicated bounded executor for background work and update the frontend to treat `/api/v2/agent/execute` as task submission, with websocket-first progress and task-status polling as fallback.

**Tech Stack:** Spring Boot MVC, Java 17, Spring `TaskExecutor`, Thymeleaf template JavaScript, JUnit 5, Mockito

---

### Task 1: Lock backend contract changes with tests

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing controller test**

Add or update a test so `POST /api/v2/agent/execute` behavior is represented as:

```java
AgentTaskResponse response = new AgentTaskResponse();
response.setTaskId("task-1");
response.setStatus("RUNNING");

when(taskApplicationService.executeV2(request)).thenReturn(response);

ResponseEntity<AgentTaskResponse> result = controller.executeAgentTask(request);

assertEquals(202, result.getStatusCode().value());
assertSame(response, result.getBody());
```

**Step 2: Write the failing service tests**

Add tests for these expectations:

```java
AgentTaskResponse response = service.executeV2(request);

assertEquals("RUNNING", response.getStatus());
assertNull(response.getFinalResult());
verify(orchestrator, never()).execute(any(TaskRequest.class));
```

and a separate test that background execution eventually updates task state and document content after the submitted runnable is executed.

**Step 3: Run targeted tests to verify they fail**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,TaskApplicationServiceTest test
```

Expected: failures showing controller status code and synchronous service assumptions no longer match the new contract.

**Step 4: Commit the test-only red state**

```bash
git add src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "test: cover async v2 task submission"
```

### Task 2: Add dedicated backend task execution boundary

**Files:**
- Create: `src/main/java/com/agent/editor/config/AgentTaskExecutionConfig.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Test: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Write the failing implementation-facing test**

Extend `TaskApplicationServiceTest` with a controllable executor so submission and background execution can be asserted separately:

```java
CapturingExecutor executor = new CapturingExecutor();
TaskApplicationService service = new TaskApplicationService(
        documentService,
        queryService,
        diffService,
        orchestrator,
        webSocketService,
        executor
);

AgentTaskResponse response = service.executeV2(request);

assertEquals("RUNNING", response.getStatus());
assertEquals(1, executor.submittedTasks());
```

**Step 2: Implement the dedicated executor wiring**

Create a Spring config bean such as:

```java
@Configuration
public class AgentTaskExecutionConfig {

    @Bean(name = "agentTaskExecutor")
    public TaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("agent-task-");
        executor.initialize();
        return executor;
    }
}
```

Inject that executor into `TaskApplicationService`, split submission logic from background logic, and add concise Chinese comments at the async boundary and terminal state write-back points.

**Step 3: Run targeted tests**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=TaskApplicationServiceTest test
```

Expected: service tests pass for immediate `RUNNING` response and deferred execution.

**Step 4: Commit backend executor changes**

```bash
git add src/main/java/com/agent/editor/config/AgentTaskExecutionConfig.java src/main/java/com/agent/editor/service/TaskApplicationService.java src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: run v2 tasks on dedicated executor"
```

### Task 3: Return correct HTTP semantics and handle rejection/failure

**Files:**
- Modify: `src/main/java/com/agent/editor/controller/AgentV2Controller.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`

**Step 1: Add failing tests for terminal failure and submission rejection**

Add tests that assert:

```java
assertEquals("FAILED", service.getTaskStatus(taskId).getStatus());
assertEquals(202, controller.executeAgentTask(request).getStatusCode().value());
```

and a separate test for executor rejection or dispatch failure that verifies no orphan `RUNNING` task remains.

**Step 2: Implement controller and failure-path logic**

Update the controller to return:

```java
return ResponseEntity.accepted().body(taskApplicationService.executeV2(request));
```

In `TaskApplicationService`, catch top-level background exceptions, write `FAILED`, and ensure document and diff updates only happen after successful orchestration completion.

**Step 3: Run targeted tests**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,TaskApplicationServiceTest test
```

Expected: both controller and service tests pass, including failure-path assertions.

**Step 4: Commit HTTP contract changes**

```bash
git add src/main/java/com/agent/editor/controller/AgentV2Controller.java src/main/java/com/agent/editor/service/TaskApplicationService.java src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java
git commit -m "feat: return accepted for v2 task submission"
```

### Task 4: Update demo page for async submit semantics

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Test: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Write the failing frontend-oriented test**

If `DemoPageTemplateTest` checks the template content, extend it to assert the page expects `/api/v2/agent/execute` plus terminal event handling rather than synchronous `finalResult` usage. At minimum, lock in the presence of async-submit markers such as polling helpers or terminal finalization hooks.

**Step 2: Update the submit flow**

Adjust `runMode()` so it behaves like:

```javascript
const response = await fetch("/api/v2/agent/execute", { ... });
if (response.status !== 202) {
    throw new Error("Agent submission failed");
}

const result = await response.json();
currentTaskId = result.taskId;
await loadTaskEvents(result.taskId);
startTaskStatusPolling(result.taskId);
showProgress(24);
```

Do not refresh document, diff, or trace until a terminal state is observed.

**Step 3: Add idempotent terminal finalization**

Refactor websocket completion handling into a shared helper such as:

```javascript
async function finalizeTask(taskId, terminalStatus) {
    if (finalizedTaskId === taskId) {
        return;
    }
    finalizedTaskId = taskId;
    setRunningState(false);
    if (terminalStatus === "COMPLETED") {
        await refreshDocument();
        await loadDiffHistory(documentId);
        await loadTrace(taskId);
    }
}
```

Use the same helper from websocket terminal events and polling fallback.

**Step 4: Run targeted tests**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DemoPageTemplateTest test
```

Expected: template test passes with the async-submit flow locked in.

**Step 5: Commit frontend changes**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "feat: update demo page for async task completion"
```

### Task 5: Run focused regression verification

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java`
- Modify: `src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java`

**Step 1: Re-run focused regression suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ControllerTest,TaskApplicationServiceTest,DemoPageTemplateTest test
```

Expected: all targeted tests pass.

**Step 2: Run broader safety verification**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: full test suite passes without regressions in existing v1 or v2 agent behavior.

**Step 3: Commit final verification updates if needed**

```bash
git add src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java src/test/java/com/agent/editor/service/TaskApplicationServiceTest.java src/test/java/com/agent/editor/controller/DemoPageTemplateTest.java
git commit -m "test: verify async v2 submission flow"
```

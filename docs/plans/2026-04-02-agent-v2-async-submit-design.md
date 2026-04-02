# Agent V2 Async Submit Design

**Date:** 2026-04-02

**Goal:** Change the native v2 agent execute flow from a blocking request/response call into an async task submission flow that returns `202 Accepted`, preserves realtime events, and updates the demo page to track completion through events and task status.

## Scope

This change targets the native `agent.v2` execution entrypoint and the demo page that consumes it.

Add:
- async task submission semantics for `POST /api/v2/agent/execute`
- background task execution through a dedicated backend executor
- explicit success and failure state write-back after background completion
- frontend submit flow that no longer expects synchronous `finalResult`
- frontend completion handling driven by events and status polling fallback

Keep:
- existing `TaskOrchestrator` runtime semantics
- existing `TaskQueryService` task/event query APIs
- existing websocket event fan-out model
- existing `/api/v1/agent/**` compatibility path

Out of scope for the first pass:
- durable job queues
- cross-node task recovery
- retry orchestration for failed agent tasks
- redesign of the `ExecutionEvent` payload model

## Current Problem

`TaskApplicationService.executeInternal(...)` currently calls `taskOrchestrator.execute(...)` inline and does not return until the whole agent workflow finishes. In Spring MVC this means the servlet request thread is occupied for the full agent lifetime.

That mismatch is especially visible because the rest of the system already behaves like an async task model:

- the backend creates a `taskId`
- task state is queryable after submission
- execution events are persisted and replayable
- websocket sessions are bound for realtime updates

The runtime itself remains intentionally synchronous and sequential inside a single task. The problem is not internal task ordering. The problem is that the whole task is still executed inside the HTTP request lifecycle.

The demo page also assumes synchronous completion today. After `POST /api/v2/agent/execute` returns, it immediately reads `finalResult`, refreshes document state, loads diff history, loads trace data, and exits the running state. That behavior will break once the backend correctly returns before execution completes.

## Design Decisions

### 1. Make native v2 execute a submission endpoint

`POST /api/v2/agent/execute` should become a submit-only API that returns `202 Accepted`.

The response body should continue using `AgentTaskResponse` for compatibility, but its meaning changes:

- `taskId`: generated synchronously during submission
- `documentId`: resolved synchronously during submission
- `status`: `RUNNING`
- `finalResult`: `null`
- `startTime`: submission time
- `endTime`: `null`

This makes the HTTP contract consistent with the existing task/event infrastructure and prevents the frontend from interpreting submission as completion.

### 2. Split application service responsibilities into submit and background run

`TaskApplicationService` should separate request-thread work from background execution work.

Request-thread responsibilities:

- validate request and resolve document
- generate `taskId` and `sessionId`
- save initial `RUNNING` task state
- bind websocket session before any runtime event is emitted
- dispatch background execution onto a dedicated executor
- return the submission response immediately

Background responsibilities:

- call `taskOrchestrator.execute(...)`
- persist final document content only after successful completion
- record diff only after document persistence succeeds
- update final `TaskState`
- convert exceptions into terminal failure state

This preserves the current application-layer ownership of persistence while releasing the servlet thread promptly.

### 3. Use a dedicated bounded executor for agent tasks

The async boundary should use a dedicated Spring-managed `TaskExecutor` or `Executor`, not the common async pool.

Requirements:

- explicit thread name prefix for diagnostics
- bounded concurrency
- bounded queue capacity
- deterministic rejection behavior

This does not make the agent work itself non-blocking. It isolates long-running agent tasks from servlet threads and prevents unbounded task accumulation from exhausting the whole process.

### 4. Fail tasks explicitly and centrally

Background execution must wrap the orchestration call in a top-level `try/catch`.

On failure:

- do not update the document
- do not record diff
- write terminal `FAILED` task state
- publish or preserve a visible failure signal through the existing event path

This ensures the frontend can leave the running state and users do not see stuck `RUNNING` tasks after an exception.

### 5. Keep websocket binding synchronous before dispatch

Session binding must still happen before the task is dispatched.

That existing invariant remains correct: if the task starts running before the websocket session is associated with the `taskId`, early events can be missed. The async refactor changes where execution happens, not when session binding must occur.

### 6. Update the demo page to use submission semantics

The frontend should treat `/api/v2/agent/execute` as task submission, not task completion.

After a successful submit:

- keep the UI in running state
- store `taskId`
- optionally replay current task events once
- wait for websocket events to drive the timeline

The frontend must no longer assume these are available immediately after submit:

- final document content
- diff results
- trace records
- terminal completion state

Those follow-up reads should happen only after a terminal event or a terminal polled status is observed.

### 7. Add status polling as websocket fallback, not as the primary control path

The demo page already has a natural realtime channel through websocket events. That should remain the primary UX path.

Add a lightweight polling fallback for the current task:

- poll `/api/v2/agent/task/{taskId}` every few seconds while the task is `RUNNING`
- stop polling on `COMPLETED` or `FAILED`
- trigger the same completion/failure finalization path as websocket terminal events

This protects the UI against missed websocket events, reconnect gaps, or page refreshes without creating a second competing progress model.

## Architecture

### Backend submit flow

1. Controller receives `POST /api/v2/agent/execute`.
2. `TaskApplicationService` resolves the document and validates the request.
3. `TaskApplicationService` creates `taskId`, resolves `sessionId`, maps `AgentType`, and saves `RUNNING`.
4. If a websocket session id is present, it binds that session to the task before dispatch.
5. `TaskApplicationService` submits background work to the dedicated agent executor.
6. Controller returns `202 Accepted` with `AgentTaskResponse(status=RUNNING)`.

### Backend background flow

1. Background runner calls `taskOrchestrator.execute(...)`.
2. On success, it updates the document and records diff if `finalContent` is present.
3. It writes the terminal task state.
4. On failure, it writes `FAILED` and emits terminal failure visibility.

### Frontend flow

1. User clicks run.
2. Page submits `POST /api/v2/agent/execute`.
3. Page stores `taskId`, stays in running mode, and optionally backfills `/events`.
4. Websocket events update the timeline and progress display.
5. Terminal success triggers document/diff/trace refresh and stops polling.
6. Terminal failure stops polling, exits running state, and leaves the timeline at the failure event.

## Error Handling

- If the executor rejects a task submission, the backend should fail the request immediately rather than persisting a fake `RUNNING` task.
- If background execution throws, the task must transition to `FAILED`.
- If websocket events are missed, frontend polling must still detect terminal state.
- If `/events` replay is empty right after submission, that is valid; the page should continue waiting for realtime events or polling.
- If the user refreshes mid-run, the page should be able to replay events and resume from polled task status.

## Testing Strategy

Backend:
- `TaskApplicationServiceTest` should cover immediate submit response, delayed background completion, failure write-back, and websocket binding order.
- `AgentV2ControllerTest` should assert `202 Accepted`.
- Any new executor config should have focused wiring tests only if non-trivial.

Frontend:
- update demo page tests if they assert old synchronous completion behavior
- verify submit keeps running state until terminal completion
- verify terminal success triggers document/diff/trace reload
- verify terminal failure exits running state without mutating displayed document content

## Risks

### In-memory executor limits

This design improves servlet-thread usage but does not add durability. Process restarts or crashes can still abandon in-flight tasks.

### Duplicate terminal handling

The page may observe completion through both websocket and polling. Finalization logic must be idempotent so terminal UI actions run once.

### Queue saturation

If the executor queue is too large, the process can accumulate too many slow agent tasks. If too small, submissions can reject aggressively. The initial values should be conservative and explicit.

## Verification

The change is complete when all of the following are true:

- `POST /api/v2/agent/execute` returns `202 Accepted` instead of waiting for completion
- servlet request threads are no longer blocked on `taskOrchestrator.execute(...)`
- task status transitions from `RUNNING` to `COMPLETED` or `FAILED` in background execution
- the demo page no longer expects synchronous `finalResult`
- demo page completion behavior is driven by terminal events or polled status fallback

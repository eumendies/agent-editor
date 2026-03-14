# Agent V2 Architecture Design

## Goal

Introduce a new `com.agent.editor.agent.v2` package that replaces the current inheritance-based agent runtime with a workflow-oriented execution kernel. The new design must support:

- ReAct execution as one strategy, not the default shape of every agent
- Planning-first execution flows
- Future multi-agent orchestration
- Clean separation between runtime state, task state, tool execution, and transport events
- Breaking changes where necessary to restore coherent boundaries

## Why A V2 Package

The current `agent` package is too coupled to evolve safely:

- `BaseAgent` is named as an abstraction but implements a concrete ReAct loop
- `ReActAgent` is forced into hook methods instead of owning its own decision model
- `AgentFactory` claims multiple modes while always returning `ReActAgent`
- `AgentState` mixes task record, runtime state, document mutation state, and API-facing data
- `EditorAgentTools` mixes tool definitions, state mutation, transport side effects, and execution
- `DocumentService` owns document CRUD, task orchestration, async execution, diffing, and response mapping

Adding Planning or multi-agent execution on top of this shape would continue the same design drift.

## Current Design Problems

### 1. False abstraction in `BaseAgent`

`BaseAgent` is not an execution framework. It is a specific loop:

1. build prompt
2. call model
3. inspect tool calls
4. execute tools
5. append tool results
6. parse completion
7. emit a UI-oriented step

That loop is suitable for one style of ReAct runtime, but not for:

- planning-only agents
- planner -> executor orchestration
- supervisor/worker multi-agent flows
- agents that return structured control decisions instead of prompt-text interpretations

### 2. Wrong extension points

Current hook methods such as `buildSystemPrompt()`, `parseResponse()`, and `extractContent()` abstract presentation details rather than control behavior. The actual variation points should be:

- how an agent makes a decision
- what tools the agent can use
- how a run terminates
- which events are emitted
- how cross-step memory is assembled

### 3. Runtime state is unstructured

`AgentState` is used as:

- mutable runtime state
- task record
- step storage
- timing storage
- document mutation carrier
- response source

This prevents stable APIs and makes async execution unreliable.

### 4. Tools are not isolated capabilities

`EditorAgentTools` directly depends on `AgentState` and `WebSocketService`, so tool execution is tightly coupled to one transport mechanism and one state model. Tools cannot be reused safely across multiple agent types.

### 5. Task orchestration lives in `DocumentService`

`DocumentService` currently:

- stores documents
- starts agent runs
- creates async tasks
- builds task responses
- computes diffs
- tracks task state

This makes every future orchestration change a document service change.

### 6. Public contract and internal contract are already inconsistent

`AgentMode.PLANNING` exists, the controller exposes it, and docs describe it, but the runtime does not support it. The codebase is already paying the cost of multi-mode design without actually having a multi-mode runtime.

## Target Design

The v2 design separates the system into six stable layers.

### 1. Agent definition layer

Package: `com.agent.editor.agent.v2.definition`

Responsibility: define what an agent is and how it decides.

Core interfaces:

```java
public interface AgentDefinition {
    AgentType type();
    Decision decide(ExecutionContext context);
}
```

```java
public sealed interface Decision {
    record ToolCalls(List<ToolCall> calls, String reasoning) implements Decision {}
    record Respond(String message, String reasoning) implements Decision {}
    record Complete(String result, String reasoning) implements Decision {}
}
```

Concrete implementations:

- `ReactAgentDefinition`
- `PlanningAgentDefinition`
- future `ReviewerAgentDefinition`
- future `WriterAgentDefinition`

An agent definition does not own a loop, websocket push, or document repository.

### 2. Runtime layer

Package: `com.agent.editor.agent.v2.runtime`

Responsibility: execute one agent run against a unified protocol.

Core types:

- `ExecutionRequest`
- `ExecutionContext`
- `ExecutionState`
- `ExecutionResult`
- `ExecutionRuntime`
- `DefaultExecutionRuntime`
- `TerminationPolicy`

The runtime algorithm becomes:

1. create execution state
2. build context
3. ask `AgentDefinition` for a `Decision`
4. if decision is `ToolCalls`, execute through tool runtime
5. append events and memory
6. stop when termination policy is met
7. return structured result

This keeps the runtime generic while letting the agent strategy vary.

### 3. Orchestration layer

Package: `com.agent.editor.agent.v2.orchestration`

Responsibility: choose and coordinate one or more agent runs to satisfy a task.

Core types:

- `TaskOrchestrator`
- `TaskRequest`
- `TaskResult`
- `OrchestrationMode`
- `SingleAgentOrchestrator`
- `PlanningThenExecutionOrchestrator`
- future `SupervisorOrchestrator`

This is where future multi-agent behavior belongs:

- first plan the work
- assign plan steps to execution agents
- aggregate intermediate results
- produce final document result

`DocumentService` should not own this logic anymore.

### 4. Tool layer

Package: `com.agent.editor.agent.v2.tool`

Responsibility: register, expose, and execute tools as reusable capabilities.

Core interfaces:

```java
public interface ToolHandler {
    String name();
    ToolDefinition definition();
    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
```

Core types:

- `ToolDefinition`
- `ToolInvocation`
- `ToolContext`
- `ToolResult`
- `ToolRegistry`
- `ToolExecutor`

Document editing tools move into dedicated classes under `tool.document`.

Benefits:

- tools can be shared by multiple agents
- transport side effects are removed from tool definitions
- tool policy is configured per agent, not per Java class

### 5. Event layer

Package: `com.agent.editor.agent.v2.event`

Responsibility: publish a normalized event stream for UI, logging, and persistence.

Core types:

- `ExecutionEvent`
- `EventType`
- `EventPublisher`
- `WebSocketEventPublisher`
- future `PersistingEventPublisher`

Representative events:

- `TASK_STARTED`
- `AGENT_SELECTED`
- `PLAN_CREATED`
- `ITERATION_STARTED`
- `DECISION_MADE`
- `TOOL_CALLED`
- `TOOL_SUCCEEDED`
- `TOOL_FAILED`
- `AGENT_RESPONDED`
- `TASK_COMPLETED`
- `TASK_FAILED`

This replaces the current `AgentStep`-first mindset with an event-first runtime.

### 6. State and memory layer

Packages:

- `com.agent.editor.agent.v2.state`
- `com.agent.editor.agent.v2.memory`

Responsibility: split runtime state from task query state.

Core state types:

- `TaskState`: externally queryable task lifecycle state
- `ExecutionState`: mutable per-run internal state
- `DocumentSnapshot`: immutable document input/output snapshots
- `TaskStatus`: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `PARTIAL`

Core memory types:

- `AgentSessionMemory`
- `LangChainChatMemoryAdapter`

This separation makes async runs, multiple agents per task, and eventual persistence viable.

## Proposed Package Layout

```text
src/main/java/com/agent/editor/agent/v2
  ├── definition
  ├── runtime
  ├── orchestration
  ├── tool
  │   └── document
  ├── state
  ├── event
  ├── memory
  └── support
```

## End-to-End Request Flow

### Single agent flow

1. controller receives task request
2. application service creates `TaskState`
3. `SingleAgentOrchestrator` selects `ReactAgentDefinition`
4. `DefaultExecutionRuntime` runs iterations
5. tools execute through `ToolRegistry`
6. runtime emits `ExecutionEvent`
7. orchestrator assembles final `TaskResult`
8. document service persists final content and diff

### Planning then execution flow

1. controller receives complex task request
2. application service creates `TaskState`
3. `PlanningThenExecutionOrchestrator` invokes `PlanningAgentDefinition`
4. planner returns a structured plan
5. orchestrator dispatches each plan step to `ReactAgentDefinition`
6. each step run emits the same `ExecutionEvent` protocol
7. orchestrator aggregates step outputs and final document
8. task result is returned and published

### Future multi-agent flow

1. supervisor agent analyzes task
2. orchestrator assigns subtasks to specialized agents
3. sub-runs share selected task context, not raw mutable state
4. event stream provides unified observability across agents
5. orchestrator consolidates final result

## Migration Strategy

### Phase 1: Build v2 core without touching v1 behavior

- add `agent.v2` packages
- add new runtime and state model
- add tool registry abstraction
- keep existing `agent` package intact

### Phase 2: Wire one vertical slice through v2

- implement `ReactAgentDefinition`
- implement `SingleAgentOrchestrator`
- route one controller/service path through v2
- adapt websocket publishing to event publisher

### Phase 3: Replace service-layer orchestration

- split task orchestration out of `DocumentService`
- move task storage into dedicated service
- move diff logic into dedicated service
- migrate response mapping to query model based on `TaskState`

### Phase 4: Add planning mode

- implement `PlanningAgentDefinition`
- add plan result model
- implement `PlanningThenExecutionOrchestrator`
- expose planning execution path via controller and query model

### Phase 5: Remove or retire v1

- delete old `BaseAgent`, `ReActAgent`, `AgentFactory`, and `EditorAgentTools`
- migrate docs and API contracts
- keep compatibility only if explicitly needed

## Design Constraints

The following rules should be treated as architectural constraints:

1. `AgentDefinition` must not depend on `WebSocketService`, `DocumentService`, or mutable global state.
2. `ToolHandler` must not mutate transport state directly.
3. Runtime emits events; UI and websocket layers subscribe to events.
4. Orchestration owns cross-agent coordination.
5. Document persistence happens outside the runtime loop.
6. Query-facing task state and runtime-facing execution state must remain separate.

## Risks

### 1. Over-engineering too early

Mitigation:

- implement only one concrete runtime
- implement only one concrete orchestrator in the first vertical slice
- keep abstractions small and driven by current needs plus planning/multi-agent requirements

### 2. Leaking LangChain4j types across the whole v2 design

Mitigation:

- isolate LangChain4j integration to memory/model adapters and ReAct/Planning definitions
- use v2-owned decision, tool, event, and state models elsewhere

### 3. Frontend breakage during event model transition

Mitigation:

- keep a temporary adapter from `ExecutionEvent` to legacy websocket payloads during migration
- switch UI to event-first payloads only after one vertical slice is stable

## Recommendation

Proceed with the v2 package immediately, but ship it incrementally:

- first stabilize the runtime protocol and task orchestration boundary
- then migrate single-agent ReAct onto v2
- then add planning orchestration on the same runtime
- then add multi-agent coordination once event and task models are proven

This path avoids further investment in the current false abstraction while keeping the migration operationally manageable.

# Planning Runtime Design

**Goal:** Define the runtime contract for `PlanningExecutionRuntime` after the agent/runtime refactor.

**Scope:** `PlanningExecutionRuntime` only handles the planning phase. It does not execute plan steps. Step execution remains in `PlanningThenExecutionOrchestrator`.

## Runtime Responsibilities

- Accept only `PlanningAgent` instances and reject other agent types.
- Build or resume `AgentRunContext` from the request and current document content.
- Append the user instruction into transcript memory before planning.
- Invoke `PlanningAgent.createPlan(AgentRunContext)`.
- Publish `TASK_STARTED`, `PLAN_CREATED`, and `TASK_COMPLETED`.
- Return `ExecutionResult<PlanResult>` with:
  - `result`: generated `PlanResult`
  - `finalMessage`: stable plan summary text
  - `finalContent`: unchanged current document content
  - `finalState`: completed state with appended AI summary message

## State Rules

- Planning does not mutate document content.
- Planning completes in a single runtime pass rather than tool-loop iterations.
- Existing transcript memory must be preserved when resuming from an external state.

## Test Focus

- Complete planning flow returns `PlanResult` and preserves document content.
- Resumed execution state is passed into the planner.
- Existing memory is preserved and appended.
- Non-planning agents are rejected.
- Old planning orchestration tests are updated to current `PlanningAgent` and `core.agent.PlanResult` APIs.

# Supervisor Runtime Design

## Goal

新增一个专门的 `SupervisorExecutionRuntime`，让 `SupervisorOrchestrator` 不再直接调用 `SupervisorAgent.decide(...)`，而是通过 runtime 执行一次 supervisor decision，与 `ToolLoopExecutionRuntime` 和 `PlanningExecutionRuntime` 保持一致的分层规范。

## Problem

当前 supervisor 链路虽然已经把上下文组装下沉到了 `SupervisorContextFactory`，但 `SupervisorOrchestrator` 仍然直接调用：

- `supervisorAgent.decide(supervisorContext)`

这带来的问题是：

- runtime 层对 supervisor 范式没有统一入口，和 planning/tool-loop 不对齐
- 事件发布语义不统一，supervisor agent 调用没有经过独立 runtime 的生命周期封装
- orchestrator 同时承担了编排控制和 agent 执行入口的职责

## Scope

这次只做“单次 supervisor decision runtime”，不把整个 `supervisor -> worker -> supervisor` 多轮循环并到 runtime。

也就是说：

- `SupervisorExecutionRuntime`
  - 负责一次 supervisor agent 调用
  - 负责类型校验、事件发布、返回 `ExecutionResult<SupervisorDecision>`
- `SupervisorOrchestrator`
  - 仍然保留 dispatch budget 循环、worker runtime 调用、worker result 累积、最终收口

## Recommended Approach

新增 `src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java`。

它的职责对齐 `PlanningExecutionRuntime`：

- 校验 `agent instanceof SupervisorAgent`
- 接受 `ExecutionRequest`
- 接受外部已组装好的 `SupervisorContext` / `AgentRunContext`
- 发布 `TASK_STARTED`
- 调用 `SupervisorAgent.decide(...)`
- 发布 `TASK_COMPLETED`
- 返回 `ExecutionResult<SupervisorDecision>`

这能把“运行一个 supervisor agent”这件事从 orchestrator 中抽离出来，但不会破坏现有 supervisor 多轮调度结构。

## Runtime Contract

### Input

- `Agent agent`
- `ExecutionRequest request`
- `AgentRunContext initialContext`

其中 supervisor 主路径应传入 `SupervisorContext`，因为 `SupervisorAgent.decide(...)` 的入参就是 `SupervisorContext`。

### Type Validation

runtime 必须拒绝非 `SupervisorAgent`：

- 若 agent 不是 `SupervisorAgent`，抛 `InCorrectAgentException`

### Context Validation

runtime 主路径要求 `initialContext instanceof SupervisorContext`。

如果 orchestrator 误传普通 `AgentRunContext`，应尽早失败，而不是在 runtime 内重新猜测或重建 `SupervisorContext`。  
`SupervisorContext` 的组装仍由 `SupervisorContextFactory` 负责。

## ExecutionResult Semantics

推荐收口规则：

- `result`
  - 直接返回 `SupervisorDecision`
- `finalMessage`
  - `AssignWorker` -> `assign worker: <workerId>`
  - `Complete` -> `complete: <summary>`
- `finalContent`
  - `AssignWorker` -> 保持当前 `context.getCurrentContent()`
  - `Complete` -> 使用 `complete.getFinalContent()`
- `finalState`
  - 追加一条 `AiChatMessage(finalMessage)`
  - 标记为 completed

这样 supervisor runtime 与 planning runtime 一样，表达的是“一次 agent 调用已经结束”，而不是“整个任务完成”。

## Event Semantics

建议沿用现有 runtime 的最小事件语义：

- `TASK_STARTED`
  - detail: `execution started`
- `TASK_COMPLETED`
  - detail: `finalMessage`

这次不新增 supervisor 专属 runtime 事件，避免扩散改动。

## Orchestrator Changes

`SupervisorOrchestrator` 改为依赖：

- `SupervisorExecutionRuntime`
- `SupervisorContextFactory`

循环内流程变成：

1. `SupervisorContext context = supervisorContextFactory.buildSupervisorContext(...)`
2. `ExecutionResult<SupervisorDecision> supervisorResult = supervisorExecutionRuntime.run(supervisorAgent, supervisorRequest, context)`
3. `SupervisorDecision decision = supervisorResult.getResult()`
4. 继续按 `AssignWorker` / `Complete` 分支走原有编排逻辑

这里的 `supervisorRequest` 可以是：

- `agentType = SUPERVISOR`
- `document = currentContent snapshot`
- `instruction = request.getInstruction()`
- `maxIterations = request.getMaxIterations()`

## Why Not Move The Whole Loop

另一种方案是把整个 supervisor 循环并进 runtime，但这次明确不做，原因是：

- 这会把 worker 调度和 agent 执行混到一起，偏离 runtime 的单一职责
- 会让 runtime 依赖 `WorkerRegistry`、worker `ExecutionRuntime`、事件分派，复杂度明显上升
- 当前你明确要求的是“参考 planning runtime，做一次 supervisor agent 运行封装”

## Testing Strategy

测试分两层：

- `SupervisorExecutionRuntimeTest`
  - 非 `SupervisorAgent` 类型校验
  - 非 `SupervisorContext` 初始上下文校验
  - `AssignWorker` 返回 `ExecutionResult<SupervisorDecision>`
  - `Complete` 返回 `ExecutionResult<SupervisorDecision>`
  - 事件发布
- `SupervisorOrchestratorTest`
  - 断言 orchestrator 通过 supervisor runtime 获取 decision
  - 不再直接依赖 `supervisorAgent.decide(...)` 的内联调用路径

## Files Likely Affected

- Create: `src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Create: `src/test/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntimeTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

## Risks

- `ExecutionRuntime` 现有 `run(..., AgentRunContext initialContext)` 签名比较宽，supervisor runtime 需要主动断言 `initialContext` 是 `SupervisorContext`
- `finalState.markCompleted()` 的语义是“本次 agent 调用结束”，不是“整个 supervisor task 结束”，测试里要避免混淆这两个层次
- 若后续还要给 supervisor agent 引入多轮内部工具调用，那应由独立的 supervisor runtime 扩展处理，不应在这次最小 runtime 中预埋复杂逻辑

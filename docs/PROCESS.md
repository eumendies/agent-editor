# AI Editor Agent 流程文档

本文档描述当前默认执行链 `agent.v2` 的真实运行流程。旧 `agent` 包仅作为 legacy 参考，不再作为主流程说明对象。

## 1. 总体流程

```text
Browser
  -> REST API
  -> TaskApplicationService
  -> TaskOrchestrator
  -> ExecutionRuntime
  -> ToolRegistry
  -> DocumentService / DiffService

并行观测：
  -> EventPublisher -> TaskQueryService / WebSocket
  -> TraceCollector -> TraceStore / Trace API
```

流程说明：

1. 前端发起 `POST /api/v1/agent/execute`
2. `TaskApplicationService` 读取文档、创建任务、映射 mode
3. `TaskOrchestrator` 根据模式选择具体工作流
4. 运行过程中产生 `ExecutionEvent` 和 `TraceRecord`
5. 完成后应用层统一持久化文档结果并记录 diff
6. 查询接口和 WebSocket 都基于同一条事件链工作

## 2. REACT 执行流程

`REACT` 模式走 `SingleAgentOrchestrator -> DefaultExecutionRuntime -> ReactAgentDefinition`。

```text
TaskRequest
  -> SingleAgentOrchestrator
  -> DefaultExecutionRuntime.run(...)
     -> ReactAgentDefinition.decide(...)
     -> if ToolCalls:
          ToolRegistry + ToolHandler.execute(...)
          更新 ExecutionState
          继续下一轮 decide
     -> if Complete/Respond:
          返回 ExecutionResult
```

关键点：

- 循环控制在 `DefaultExecutionRuntime`
- `ReactAgentDefinition` 只负责单轮决策
- 当前文档内容和历史工具结果会回灌到下一轮 prompt
- worker 场景下只暴露白名单工具，避免越权调用

## 3. PLANNING 执行流程

`PLANNING` 模式走 `PlanningThenExecutionOrchestrator`。

```text
TaskRequest
  -> PlanningAgentDefinition.createPlan(...)
  -> PlanResult(step1, step2, ...)
  -> for each step:
       ExecutionRuntime.run(executionAgent, currentDocumentSnapshot)
       currentContent = 上一步输出
  -> TaskResult
```

关键点：

- planner 只生成计划，不直接调用工具
- 每个步骤都基于上一步输出的文档内容继续执行
- `PLAN_CREATED` 和 `planning.step.dispatch` 会进入 event/trace

## 4. SUPERVISOR 执行流程

`SUPERVISOR` 模式走 `SupervisorOrchestrator`。

```text
TaskRequest
  -> SupervisorAgentDefinition.decide(...)
     -> AssignWorker / Complete
  -> if AssignWorker:
       WorkerRegistry.get(workerId)
       ExecutionRuntime.run(worker.agent, worker-scoped request)
       记录 WorkerResult
       回灌给下一轮 supervisor
  -> if Complete:
       返回最终 TaskResult
```

关键点：

- supervisor 只做调度和收口
- worker 仍然复用统一 `ExecutionRuntime`
- 每个 worker 都有自己的 `allowedTools`
- 第一版 supervisor 策略是顺序串行调度，后续可替换成更智能的实现

## 5. WebSocket 与步骤流

当前步骤展示链是：

```text
ExecutionEvent
  -> WebSocketEventPublisher
  -> LegacyEventAdapter
  -> WebSocketMessage / AgentStep
```

这条链是兼容层，不是内部主模型。

当前真实行为：

- WebSocket 建立后服务端返回 `CONNECTED`
- 可以通过查询参数 `taskId` 或消息 `SUBSCRIBE` 绑定任务
- `/execute` 是同步调用，因此如果请求里已带 `sessionId`，服务端会在执行前预绑定 `taskId`
- 这样即使前端不额外发 `SUBSCRIBE`，运行期间的 event 也不会丢

## 6. Trace 调试链

当前 trace 是独立于步骤流的高保真调试链：

```text
ReactAgentDefinition
  -> MODEL_REQUEST / MODEL_RESPONSE

DefaultExecutionRuntime
  -> STATE_SNAPSHOT / TOOL_INVOCATION / TOOL_RESULT

PlanningThenExecutionOrchestrator
  -> ORCHESTRATION_DECISION

SupervisorOrchestrator
  -> ORCHESTRATION_DECISION
```

trace 存储目前是：

- `DefaultTraceCollector`
- `InMemoryTraceStore`

当前适合本地调试和演示，不是持久化方案。

## 7. 文档更新与 Diff

`TaskApplicationService` 在 orchestrator 返回后统一处理结果：

1. 读取原始文档内容
2. 执行任务并得到 `TaskResult`
3. 如果有 `finalContent`，更新文档
4. 调用 `DiffService` 记录前后差异
5. 更新 `TaskState`

这个边界很重要：

- orchestrator 负责“任务怎么跑”
- runtime 负责“单 agent 怎么循环”
- 应用层负责“结果怎么落文档、怎么生成 diff”

## 8. Legacy 运行时

旧 `agent` 包仍然保留：

- `BaseAgent`
- `ReActAgent`
- `AgentFactory`
- `EditorAgentTools`

但它不再是当前主链的一部分。阅读和扩展新功能时，应该优先以 `agent.v2` 为准。

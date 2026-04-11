# AI Agent Editor 流程文档

本文档描述当前仓库里真实存在的 agent 执行流程、状态流转和文档落盘边界，不再使用已经过时的旧包层级叙述。

## 1. 总体流程

当前主链可以概括为：

```text
Browser / API Client
  -> AgentController
  -> TaskApplicationService
  -> TaskOrchestrator
     -> ReActAgentOrchestrator
     -> PlanningThenExecutionOrchestrator
     -> SupervisorOrchestrator
     -> ReflexionOrchestrator
  -> runtime / tools / memory
  -> PendingDocumentChangeService
  -> DiffService / DocumentService

并行输出：
  -> EventPublisher -> TaskQueryService / WebSocket
  -> TraceCollector -> TraceStore / Trace API
```

流程说明：

1. 客户端调用 `POST /api/agent/execute`
2. `TaskApplicationService` 校验文档、创建任务 ID、映射 mode
3. 任务被异步提交到专用执行线程
4. `TaskOrchestrator` 按 `AgentType` 路由到具体 orchestrator
5. orchestrator 通过 runtime、tools、memory 完成任务
6. 如果最终正文发生变化，结果先写入 pending change，而不是直接覆盖文档
7. 用户后续通过 diff 接口决定应用或丢弃改动

## 2. TaskApplicationService 的职责

`TaskApplicationService` 是当前应用层入口，主要负责：

- 校验 `documentId`
- 生成 `taskId` 和 `sessionId`
- 将外部 `AgentMode` 映射到内部 `AgentType`
- 创建 `TaskState`
- 异步提交任务执行
- 在执行结束后更新任务终态
- 当正文实际变化时保存 pending change

几个关键边界：

- controller 不直接驱动 orchestrator
- orchestrator 不直接落正式文档
- 应用层负责把 agent 输出转换成“待确认改动”

## 3. REACT 流程

`REACT` 模式走 `ReActAgentOrchestrator`。

```text
TaskRequest
  -> ReactAgentContextFactory.prepareInitialContext(...)
  -> resolve document tool mode
  -> build ExecutionRequest
  -> ToolLoopExecutionRuntime.run(...)
  -> TaskResult
```

关键点：

- 单个 agent 完成整个任务
- 工具权限由 `ExecutionToolAccessPolicy` 决定
- runtime 驱动工具循环，orchestrator 只负责准备上下文和请求

## 4. PLANNING 流程

`PLANNING` 模式走 `PlanningThenExecutionOrchestrator`。

```text
TaskRequest
  -> Planning runtime
  -> PlanResult(step1, step2, ...)
  -> for each step:
       build current document snapshot
       run execution agent
       currentContent = previous step output
  -> TaskResult
```

关键点：

- planner 负责生成结构化计划
- 每个计划步骤仍由执行 agent 落地
- 上一步文档输出会成为下一步输入

## 5. SUPERVISOR 流程

`SUPERVISOR` 模式走 `SupervisorOrchestrator`。

```text
TaskRequest
  -> build SupervisorContext
  -> SupervisorExecutionRuntime.run(...)
     -> AssignWorker / Complete
  -> if AssignWorker:
       worker runtime executes selected worker
       summarize worker result
       rebuild SupervisorContext for next round
  -> if Complete:
       TaskResult
```

关键点：

- supervisor 只做分派和收口，不直接执行工具编辑
- worker 仍通过统一 execution runtime 执行
- 当前 `WorkerRegistry` 提供可选 worker，具体是否分派由 supervisor 决策
- worker 输出的正文会成为下一轮 supervisor 看到的最新内容

## 6. REFLEXION 流程

`REFLEXION` 模式走 `ReflexionOrchestrator`。

```text
TaskRequest
  -> actor run
  -> critic run
  -> if verdict == PASS:
       complete
     else:
       critique feeds back into actor context
       next round
```

关键点：

- actor 状态跨轮保留
- critic 每轮 fresh，避免把上轮评审过程本身继续污染下一轮判定
- critic 的反馈会回灌给 actor，形成修订闭环

## 7. Event Flow

当前事件流由 `WebSocketEventPublisher` 统一发布：

```text
ExecutionEvent
  -> TaskQueryService.appendEvent(...)
  -> WebSocketService.sendEventToTask(...)
```

这意味着：

- 查询接口和实时推送共用同一条 `ExecutionEvent` 流
- `/api/agent/task/{taskId}/events` 与 WebSocket 不是两套独立状态源
- 如果请求携带 `sessionId`，应用层会先绑定 session，再启动任务，避免首批事件丢失

## 8. Trace Flow

当前 trace 链仍然存在，并通过 `TraceStore` 暴露 HTTP 查询接口：

```text
orchestrator / runtime / model interaction
  -> TraceCollector
  -> TraceStore
  -> /api/agent/task/{taskId}/trace
```

当前适用场景：

- 本地调试
- 任务执行回放
- 分类汇总和阶段观察

当前不应把它理解为持久化审计系统。

## 9. 文档变更落盘流程

当前文档改动不是“执行完立即写回”。

实际边界是：

1. orchestrator 返回 `TaskResult`
2. 应用层比较 `originalContent` 和 `finalContent`
3. 如果内容无变化，清掉旧 pending change
4. 如果内容有变化，保存 pending change
5. 用户通过 diff 接口决定应用或丢弃

只有 `POST /api/v1/diff/document/{documentId}/apply` 才会同时：

- 更新正式文档正文
- 记录 diff 历史
- 清除 pending change

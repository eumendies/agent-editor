# Agent Trace Design

**Date:** 2026-03-15

## Goal

为 `agent.v2` 增加一套面向开发调试的高保真调用链追踪能力，能够查看一次 task 内每一步的 prompt、模型响应、工具调用参数、工具结果、编排决策和运行时状态，而不是依赖零散日志排查。

第一版以 `内存存储 + 查询接口` 为目标，允许保存完整 prompt、完整工具参数和完整结果，不做脱敏截断。

## Why Existing Events Are Not Enough

当前 `agent.v2` 已经有：

- `ExecutionRuntime -> EventPublisher -> TaskQueryService`
- `ExecutionEvent`
- WebSocket 推送和步骤查询

但现有 `ExecutionEvent` 只有：

- `type`
- `taskId`
- `message`

它只适合：

- 前端步骤流
- 轻量状态展示
- 简单运行轨迹

它不适合：

- 查看完整 prompt
- 查看模型原始响应
- 查看 tool arguments
- 查看 tool result 和文档变化
- 查看 planning/supervisor 的编排决策细节

因此不能继续把“调试追踪”硬塞进 `ExecutionEvent`，否则会把前端事件流、trace、WebSocket 三种职责耦在一起。

## Recommended Architecture

推荐新增一条独立的 `trace` 链路：

- `ExecutionEvent`
  - 继续给页面步骤流和 WebSocket 使用
- `TraceRecord`
  - 仅给开发调试使用
- `TraceCollector`
  - 由 runtime / definition / orchestrator 在关键点写入
- `TraceStore`
  - 第一版使用内存存储
- `TraceController`
  - 提供 trace 查询接口

这条链路与现有 `EventPublisher` 并行存在，不替换当前前端事件流。

## Core Model

### 1. TraceCategory

建议定义以下类别：

- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_INVOCATION`
- `TOOL_RESULT`
- `ORCHESTRATION_DECISION`
- `STATE_SNAPSHOT`

### 2. TraceRecord

每条 trace 建议统一使用固定外层字段 + 灵活 payload：

- `traceId`
- `taskId`
- `timestamp`
- `category`
- `stage`
- `agentType`
- `workerId`
- `iteration`
- `payload`

其中 `payload` 使用 `Map<String, Object>`，用于承载不同阶段的不同字段，避免随着 trace 类型增加反复改 schema。

### 3. Trace Payload Examples

`MODEL_REQUEST`：

- `systemPrompt`
- `userPrompt`
- `toolSpecifications`
- `documentSnapshot`

`MODEL_RESPONSE`：

- `rawText`
- `toolCalls`
- `decisionType`

`TOOL_INVOCATION`：

- `toolName`
- `arguments`
- `currentContent`

`TOOL_RESULT`：

- `toolName`
- `message`
- `updatedContent`

`ORCHESTRATION_DECISION`：

- `plan`
- `selectedWorker`
- `workerInstruction`
- `summary`

`STATE_SNAPSHOT`：

- `currentContent`
- `toolResults`
- `maxIterations`

## Collection Points

### 1. ReactAgentDefinition

这里是模型交互最完整的地方，适合记录：

- `MODEL_REQUEST`
- `MODEL_RESPONSE`

重点：

- 在 `chatModel.chat(...)` 调用前采集完整 system/user prompt
- 在调用后采集原始模型文本和 tool calls

### 2. DefaultExecutionRuntime

这里适合记录：

- `STATE_SNAPSHOT`
- `TOOL_INVOCATION`
- `TOOL_RESULT`

重点：

- 每轮开始记录当前文档状态和累计工具结果
- 每次工具执行前记录调用参数
- 每次工具执行后记录结果和内容变化

### 3. PlanningThenExecutionOrchestrator

这里适合记录 planning 相关的 `ORCHESTRATION_DECISION`：

- 生成了什么 plan
- 当前在执行哪个 plan step
- 每一步执行顺序是什么

### 4. SupervisorOrchestrator

这里适合记录 supervisor 相关的 `ORCHESTRATION_DECISION`：

- 选择了哪个 worker
- 下发了什么 instruction
- worker 返回了什么 summary
- supervisor 最终如何汇总

## Query Interface

第一版只需要两个接口：

- `GET /api/v1/agent/task/{taskId}/trace`
  - 返回完整 trace 列表
- `GET /api/v1/agent/task/{taskId}/trace/summary`
  - 返回简化后的摘要视图

其中 `summary` 用于快速浏览链路，而完整 `trace` 用于排查 prompt/tool/result 细节。

## UI Direction

第一版不需要独立的新前端系统，只需要在现有 demo 页上增加一个简单 `Trace Inspector` 面板或抽屉。

展示顺序建议按时间线展开：

- iteration
- prompt
- model response
- tool call
- tool result
- next state

目标是让开发者能一眼看到：

- 模型看到了什么
- 为什么做出当前 decision
- 工具执行后状态怎么变化
- 编排层如何继续推进

## Why Not Just Use Logs

普通日志的问题是：

- 结构不稳定
- 不便按 task 聚合
- 无法直接驱动页面展示
- 很难表达多 agent 编排层的语义
- 很难在后续扩展到文件导出或链路平台

日志仍然可以保留，但应当只作为辅助输出，而不是 agent 调试的主数据源。

## Why Not Expand ExecutionEvent

不建议直接把 `ExecutionEvent` 扩成 trace 大对象，原因是：

- 会把前端步骤流和调试 trace 混在一起
- WebSocket 消息会变重
- 旧兼容层 `LegacyEventAdapter` 会持续复杂化
- 后续想区分“用户可见事件”和“开发者可见 trace”会变得困难

因此 trace 应该独立建模。

## Why Not Use ChatMemory Directly

不建议第一版直接把 `agent.v2` 回退成 provider 绑定的 `ChatMemory` 模型。

原因：

- `agent.v2` 当前的目标是保持 runtime/state/orchestration 的独立边界
- `ChatMemory` 更适合作为模型消息构建机制，而不是系统 trace 底座
- trace 需要覆盖 supervisor/planning/tool/runtime 等不止模型消息的节点

如果未来需要，可以让 trace 和 memory 并存：

- memory 用于“下一轮模型该看什么”
- trace 用于“开发者想看系统发生了什么”

## Future Compatibility

第一版虽然只做内存存储，但对象模型应当兼容后续扩展：

- `InMemoryTraceStore` 可替换成文件落盘实现
- `DefaultTraceCollector` 可追加 exporter 到 OpenTelemetry
- `TraceRecord` 可映射到 span/event，而不需要重做 runtime 埋点

因此第一版的重点不是“功能大而全”，而是把 trace 边界和采集点定义正确。

## Recommended First Iteration

第一版建议严格控制范围：

1. 新增 `trace` 模型和内存存储
2. 在 `ReactAgentDefinition` 采集模型请求/响应
3. 在 `DefaultExecutionRuntime` 采集状态、工具调用和工具结果
4. 在 `PlanningThenExecutionOrchestrator` 和 `SupervisorOrchestrator` 采集编排决策
5. 提供 trace 查询接口
6. 在 demo 页增加简单 trace 查看面板

不要在第一版做：

- 数据库存储
- 日志平台接入
- 脱敏规则系统
- 复杂检索
- 独立 observability 产品化界面

## Success Criteria

完成后，开发者应该能对任意一个 task 做到：

- 查看每轮 prompt 和模型原始响应
- 查看每次 tool call 的参数和结果
- 查看文档状态如何随着 tool result 演进
- 查看 planning/supervisor 的编排决策
- 在页面中按 task 回放完整调用链

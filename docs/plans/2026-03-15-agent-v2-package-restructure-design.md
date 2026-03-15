# Agent V2 Package Restructure Design

**Date:** 2026-03-15

## Goal

对 `agent.v2` 做一次纯目录与包结构重组，让代码更符合当前系统的真实边界：

- `core` 只承载通用内核
- `react`、`planning`、`supervisor` 各自承载模式专属实现
- `task` 承载跨模式任务编排入口
- `tool`、`trace`、`event` 保持横切子系统独立

这轮重构不改变行为，不顺手修改 runtime、trace、tool policy 或 controller 语义，只做目录/包边界整理和装配文件拆分。

## Why Current Structure Is Not Ideal

当前 `agent.v2` 的问题不是“文件太多”，而是“顶层目录语义已经和系统真实结构错位”。

### 1. `definition` 混放了通用抽象和具体模式

当前：

- `AgentDefinition`
- `AgentType`
- `Decision`
- `ToolCall`
- `ReactAgentDefinition`
- `PlanningAgentDefinition`
- `SequentialSupervisorAgentDefinition`
- `SupervisorAgentDefinition`

这导致：

- 通用 agent 语言和具体模式实现混在一起
- ReAct / Planning / Supervisor 没有自己的收口空间

### 2. `orchestration` 混放了通用 task 协议和模式专属工作流

当前：

- `TaskOrchestrator`
- `TaskRequest`
- `TaskResult`
- `SingleAgentOrchestrator`
- `RoutingTaskOrchestrator`

与：

- `PlanningThenExecutionOrchestrator`
- `PlanResult`
- `PlanStep`
- `SupervisorOrchestrator`
- `SupervisorDecision`
- `WorkerDefinition`

都在同一个目录下。

问题在于：

- `PlanningThenExecutionOrchestrator` 是 planning 模式专属
- `SupervisorOrchestrator` 是 supervisor 模式专属
- 但它们和通用任务入口放在一起，模糊了系统边界

### 3. `AgentV2Config` 承担了全部装配责任

虽然包已经在代码层做了一些分层，但装配关系仍然全部挤在一个配置类里，目录结构没有在装配层得到体现。

### 4. `trace` 子系统已经独立，但 `event` 还保留大量兼容语义

`trace` 已经是独立横切能力，这说明 `agent.v2` 本身已经不是单纯的“执行逻辑包”，而是一个带有独立观察层的运行时系统。

因此包结构应当更明确地区分：

- 内核
- 模式
- 任务入口
- 横切子系统

## Recommended Structure

推荐使用“模式优先 + 内核独立”的目录结构：

```text
agent/v2
  ├── core
  │   ├── agent
  │   ├── runtime
  │   └── state
  ├── react
  ├── planning
  ├── supervisor
  ├── task
  ├── tool
  ├── trace
  └── event
```

这是本次设计的核心。

## Package Mapping

### 1. `core`

`core` 只放不带模式语义的通用内核。

建议结构：

```text
core/agent
  AgentDefinition
  AgentType
  Decision
  ToolCall

core/runtime
  DefaultExecutionRuntime
  ExecutionContext
  ExecutionRequest
  ExecutionResult
  ExecutionRuntime
  ExecutionStateSnapshot
  TerminationPolicy

core/state
  DocumentSnapshot
  ExecutionState
  TaskState
  TaskStatus
```

判定标准：

- ReAct / Planning / Supervisor 都会依赖它
- 不应该出现在某个模式目录下

### 2. `react`

只放 ReAct 模式专属实现：

- `ReactAgentDefinition`

以后如果再加：

- ReAct prompt builder
- ReAct memory strategy
- ReAct response parser

也应该继续留在这里。

### 3. `planning`

只放 Planning 模式专属对象：

- `PlanningAgentDefinition`
- `PlanResult`
- `PlanStep`
- `PlanningThenExecutionOrchestrator`

这里的重要原则是：

`PlanningThenExecutionOrchestrator` 虽然名字里带 orchestrator，但它不是“全局任务入口”，而是 planning 模式的内部工作流实现。

### 4. `supervisor`

只放 Supervisor 多 agent 模式专属对象：

- `SupervisorAgentDefinition`
- `SequentialSupervisorAgentDefinition`
- `SupervisorContext`
- `SupervisorDecision`
- `SupervisorOrchestrator`
- `WorkerDefinition`
- `WorkerRegistry`
- `WorkerResult`

以后如果加入：

- LLM supervisor
- worker capability routing
- parallel worker dispatch

也都应该继续留在 `supervisor`。

### 5. `task`

只放跨模式任务调度入口：

- `TaskOrchestrator`
- `TaskRequest`
- `TaskResult`
- `SingleAgentOrchestrator`
- `RoutingTaskOrchestrator`

这部分负责：

- “收到一个 task 后该走哪条模式链路”
- 而不是某个模式内部怎么跑

### 6. `tool`

`tool` 保持独立，不放进 `core`。

原因：

- 它是横切基础设施，不是执行内核语言本身
- 后续工具种类会继续膨胀
- 工具注册、能力域划分、审计都可能继续扩展

### 7. `trace`

`trace` 保持独立，不放进 `event` 或 `core`。

原因：

- 它已经是独立子系统
- 它的关注点是开发调试观察，不是用户可见事件
- 后续可能追加文件落盘、OpenTelemetry exporter

### 8. `event`

`event` 继续独立。

原因：

- `ExecutionEvent` 仍然服务于前端步骤流和 WebSocket
- `trace` 与 `event` 已经是两个语义不同的系统

## Config Restructure

目录重组如果只搬 Java 文件而不拆装配，收益会打一半折扣。

因此建议同时把 `AgentV2Config` 拆成以下几类配置：

- `ToolConfig`
- `TraceConfig`
- `ReactAgentConfig`
- `PlanningAgentConfig`
- `SupervisorAgentConfig`
- `TaskOrchestratorConfig`

拆分原则：

- 工具注册单独装配
- trace store / collector 单独装配
- 各模式自己的 agent / orchestrator 在各自配置里完成装配
- 最后由 `TaskOrchestratorConfig` 汇总 routing

这样代码目录和 Spring bean 装配关系才是一致的。

## Migration Principles

### 1. This is a packaging refactor, not a behavior refactor

本次只做：

- 包名迁移
- import 修正
- 配置拆分

不做：

- runtime 逻辑修改
- trace 语义修改
- tool policy 修改
- controller/API 改动

### 2. Move in dependency order

建议严格按依赖顺序搬迁：

1. 纯模型
2. 核心接口和 runtime
3. 模式实现
4. 配置装配

避免一次性修改太多层，导致无法判断问题出在哪。

### 3. Keep tests structurally aligned

测试包也要同步按新目录整理，否则主代码重组后测试还保留旧包路径，会让结构感知再次失真。

## Proposed Final Layout

```text
src/main/java/com/agent/editor/agent/v2
  ├── core
  │   ├── agent
  │   ├── runtime
  │   └── state
  ├── react
  ├── planning
  ├── supervisor
  ├── task
  ├── tool
  │   └── document
  ├── trace
  └── event
```

测试目录同步变成：

```text
src/test/java/com/agent/editor/agent/v2
  ├── core
  ├── react
  ├── planning
  ├── supervisor
  ├── task
  ├── tool
  ├── trace
  └── event
```

## Success Criteria

完成后应满足：

- 开发者能一眼区分内核、模式、任务入口和横切子系统
- ReAct / Planning / Supervisor 的文件不再混放
- `core` 不包含模式专属对象
- `task` 不包含 planning/supervisor 的专属模型
- 配置文件不再由单个 `AgentV2Config` 承担全部装配
- 行为不变，现有测试保持通过

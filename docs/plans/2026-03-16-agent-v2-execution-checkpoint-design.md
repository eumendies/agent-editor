# Agent V2 Execution Checkpoint Design

**Date:** 2026-03-16

## Goal

解决当前 Plan-and-Execute / Supervisor worker 链路中“后续子 Agent 看不到前序执行上下文”的问题，同时为后续 `resume`、`human-in-the-loop` 和跨 step 续跑提供一个统一的可恢复执行快照模型。

这次设计的目标不是把 `agent.v2` 重写成完全依赖 LangChain4j memory 的系统，而是在保留现有 custom runtime/orchestrator 结构的前提下，引入一套显式的执行 checkpoint 机制。

## Problem

当前实现里：

- `[PlanningThenExecutionOrchestrator.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java)` 只把 `currentContent` 串给下一个 plan step
- `[DefaultExecutionRuntime.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java)` 内部维护的 `ExecutionState` 只在单次 `run()` 生命周期内可见
- `[ReactAgentDefinition.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java)` 只能看到本次 `run()` 内累积的 `toolResults`

这导致两个问题：

1. plan step 之间只能传递“文档结果”，不能传递“执行过程”
2. runtime 没有显式的可恢复 checkpoint，后续很难支持人工介入后恢复执行

另外，`ExecutionState.toolResults` 本身已经带有明显的 ReAct 假设：它把“工具结果”硬编码成了所有 agent 都共享的顶层状态，而不是让不同 agent 通过各自的 memory/transcript 消费上下文。

## Recommended Architecture

推荐保留 `ExecutionState` 这个名称，并把它收敛成“可持久化、可恢复、可跨子任务复用的执行快照”。

同时：

- 用 `ExecutionStage` 替代 `completed` / `ExecutionStatus`
- 去掉 `toolResults`
- 新增抽象的 `ExecutionMemory`
- 让 runtime 接收外部传入的 `ExecutionState`
- 让 runtime 在返回结果时带回更新后的 `ExecutionState`

整体职责边界如下：

- `ExecutionState`
  - 当前执行快照
- `ExecutionStage`
  - 这个快照当前所处阶段
- `ExecutionMemory`
  - 模型可见的上下文历史
- `DefaultExecutionRuntime`
  - 推进 `ExecutionState`
- `PlanningThenExecutionOrchestrator` / `SupervisorOrchestrator`
  - 决定 `ExecutionState` 是否跨 step / worker 复用

## Core Model

### 1. ExecutionState

建议收敛成：

```java
public record ExecutionState(
        int iteration,
        String currentContent,
        ExecutionMemory memory,
        ExecutionStage stage,
        String pendingReason
) {
}
```

字段语义：

- `iteration`
  - 当前已经推进到第几轮 runtime 决策
- `currentContent`
  - 文档真实状态，始终是 source of truth
- `memory`
  - 模型可见的历史，不直接等同于文档状态
- `stage`
  - 当前 checkpoint 处于运行中、已完成、等待人工、失败等哪个阶段
- `pendingReason`
  - 用于等待人工或失败恢复时记录原因

### 2. ExecutionStage

建议定义为：

```java
public enum ExecutionStage {
    RUNNING,
    COMPLETED,
    WAITING_FOR_HUMAN,
    FAILED
}
```

这样可以同时表达：

- 正常推进
- 正常完成
- 中途暂停，等待人工输入
- 异常中断但保留快照

### 3. ExecutionMemory

`ExecutionMemory` 不应该直接等于 LangChain4j 的 `ChatMemory`。

推荐先定义一个本地抽象：

```java
public interface ExecutionMemory {
}
```

第一版只实现一类 transcript memory：

```java
public record ChatTranscriptMemory(
        List<ExecutionMessage> messages
) implements ExecutionMemory {
}
```

这里的关键是：

- core runtime 只依赖本地抽象
- `ReactAgentDefinition` 再把 transcript 转成 LangChain4j message
- 以后如果 planning/supervisor 需要不同 memory 结构，可以继续扩展

### 4. ExecutionMessage

建议定义轻量本地消息模型，而不是直接把 `SystemMessage/UserMessage/AiMessage/ToolExecutionResultMessage` 放进 core state。

例如：

- `SystemExecutionMessage`
- `UserExecutionMessage`
- `AiExecutionMessage`
- `ToolExecutionResultExecutionMessage`

这样做有两个收益：

1. `core.state` 不会被 LangChain4j 类型绑死
2. transcript 可以序列化、落库、恢复，而不用处理第三方模型对象的兼容性

## Runtime API

当前 `ExecutionRuntime` 只有：

```java
ExecutionResult run(AgentDefinition definition, ExecutionRequest request);
```

建议增加可恢复入口：

```java
ExecutionResult run(AgentDefinition definition, ExecutionRequest request, ExecutionState initialState);
```

同时保留当前入口，内部转调新的 overload：

- 如果调用方没有显式提供 state
  - runtime 自己基于 `request.document().content()` 初始化一个 fresh checkpoint
- 如果调用方提供了已有 state
  - runtime 从该 checkpoint 继续推进

## ExecutionResult

建议从：

```java
public record ExecutionResult(String finalMessage, String finalContent) {
}
```

扩成：

```java
public record ExecutionResult(
        String finalMessage,
        String finalContent,
        ExecutionState finalState
) {
}
```

这样 orchestrator 不需要重新拼装 checkpoint，而是直接复用 runtime 返回的最终快照。

## DefaultExecutionRuntime Responsibilities

`[DefaultExecutionRuntime.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java)` 的职责应当固定为：

1. 接收 `ExecutionRequest + ExecutionState`
2. 构造 `ExecutionContext`
3. 调用 agent `decide(...)`
4. 执行 tool calls
5. 维护 `currentContent`
6. 把本轮 `AiMessage / ToolExecutionResult` 写回 memory
7. 返回带 `finalState` 的 `ExecutionResult`

它不负责：

- 跨 task 持久化 state
- 决定 step 间是否共享 memory
- 决定 worker 间是否共享 memory

这些都属于 orchestrator 的职责。

## ReactAgentDefinition Responsibilities

`[ReactAgentDefinition.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java)` 不再依赖 `ExecutionState.toolResults()`。

新的职责应当是：

1. 从 `ExecutionState.memory` 里读取 transcript
2. 转换成 LangChain4j messages
3. 构建 `ChatRequest`
4. 调用模型
5. 把响应翻译成 `Decision`

注意：

- `ReactAgentDefinition` 不直接改写 `ExecutionState`
- runtime 才是唯一的状态推进者

这可以避免 agent 和 runtime 同时修改 memory，导致状态源不清晰。

## Orchestrator Flow

### 1. PlanningThenExecutionOrchestrator

推荐流程：

1. planner 生成 `PlanResult`
2. orchestrator 初始化一个共享 `ExecutionState`
3. 每进入一个新 plan step：
   - 更新 `ExecutionRequest.instruction`
   - 在 memory 中追加一条 step 边界 user message
   - 调 `executionRuntime.run(..., currentState)`
4. 拿到 `ExecutionResult.finalState`
5. 把 `finalState` 传给下一个 step

关键点：

- `currentContent` 继续串行传递
- memory 也显式串行传递
- 这样后续 step 不仅能看到上一步改出了什么，还能看到上一步是怎么改的

### 2. SupervisorOrchestrator

这个设计同样适用于：

- 每个 worker 一个独立 `ExecutionState`
- 多个 worker 共享一个 `ExecutionState`

具体采用哪种模式，应该由 supervisor orchestration 决定，而不是 runtime 决定。

第一版建议保守处理：

- worker 默认各自独立 state
- supervisor 如有需要，再显式构造共享 state

这样可以避免 worker memory 相互污染。

## Why Not Put ChatMemory Directly In ExecutionState

虽然把 LangChain4j `ChatMemory` 直接放进 `ExecutionState` 能更快打通功能，但不建议这么做，主要原因有三个：

1. `core.state` 会直接依赖模型库类型
2. 持久化和恢复会依赖第三方对象的序列化行为
3. 非 ReAct agent 也会被迫接受 chat-specific 的状态模型

所以更稳的边界是：

- `ExecutionState` 保存本地 transcript 抽象
- `ReactAgentDefinition` 负责桥接到 LangChain4j message

## Relationship With ToolExecutionMemoryBridge

当前工作区里存在一个未提交草稿：

- `[ToolExecutionMemoryBridge.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/core/runtime/ToolExecutionMemoryBridge.java)`

它表达的方向与本设计一致：runtime 执行完工具后，需要把工具交互同步到 memory。

但在本设计下，不建议让 bridge 成为长期核心抽象。更自然的做法是：

- runtime 直接更新 `ExecutionState.memory`
- 如果后续确实需要让不同 agent 定制“如何把 tool execution 写入 memory”，再把该 bridge 收敛成内部协作者

换句话说，`ToolExecutionMemoryBridge` 可以作为过渡性实现草稿，但不应主导最终 API。

## Error Handling

第一版需要明确以下行为：

- 模型失败
  - `ExecutionStage` 进入 `FAILED`
  - 保留最后一个 checkpoint
- 人工中断
  - `ExecutionStage` 进入 `WAITING_FOR_HUMAN`
  - 保留 `pendingReason`
- 正常完成
  - `ExecutionStage` 进入 `COMPLETED`
  - 返回 `finalState`

即使第一版还没有完整的人工介入 UI，也应该先把状态模型预留出来。

## Testing Strategy

重点测试：

1. `DefaultExecutionRuntimeTest`
   - 支持从外部传入已有 `ExecutionState`
   - 工具执行后会更新 `currentContent` 和 memory
   - 返回 `finalState`

2. `ReactAgentDefinitionTest`
   - 能从 transcript memory 构造模型消息
   - 不再依赖 `toolResults`

3. `PlanningThenExecutionOrchestratorTest`
   - 下一个 step 能看到前一个 step 的 memory
   - step 边界 instruction 会正确写入 transcript

4. `SupervisorOrchestratorTest`
   - worker state 共享/隔离策略保持可控

## Non-Goals

这轮不做：

- 持久化存储 `ExecutionState`
- 完整的人机中断恢复 UI
- 让 planner 和 supervisor 立即全部迁移到 transcript 驱动
- 用 memory 推导文档最终状态

文档真实状态仍然只能来自：

- `ExecutionState.currentContent`

## Summary

这次改造的本质不是“给 ReAct 加 chat memory”，而是把 `agent.v2` 的执行过程从“只在一次 run() 里短暂存在”提升为“可以显式传递、显式恢复、显式复用的 checkpoint”。

推荐最终边界是：

- `ExecutionState` = 可恢复执行快照
- `ExecutionStage` = 当前推进阶段
- `ExecutionMemory` = 模型可见历史
- `DefaultExecutionRuntime` = checkpoint 推进器
- `Orchestrator` = checkpoint 复用策略持有者

# Supervisor Context Factory Design

## Goal

把 supervisor 范式里散落在 `SupervisorOrchestrator` 和 `HybridSupervisorAgent` 中的上下文组装逻辑收敛到单一的 `SupervisorContextFactory`，让 orchestrator 只负责流程控制，agent 只负责决策。

## Current Problem

当前 supervisor 这条线存在两类职责混杂：

- `SupervisorOrchestrator`
  - 负责 dispatch budget、事件发布、worker runtime 调用
  - 同时还在组装 `SupervisorContext`
  - 还在组装 worker 执行态 `AgentRunContext`
  - 还在把 worker 执行结果摘要写回 conversation memory
- `HybridSupervisorAgent`
  - 负责 reviewer pass / no-progress / candidate filtering / final decision
  - 同时还在渲染 routing model 所需的 candidate 文本、worker result 文本、fallback instruction

这样会带来两个问题：

- supervisor 范式的上下文语义没有单一出口，后续继续引入新 supervisor agent 时容易复制逻辑
- orchestrator 和 agent 都知道太多上下文细节，破坏了 `AgentContextFactory` 统一负责上下文与模型调用输入组装的方向

## Recommended Approach

新增 `SupervisorContextFactory`，由它统一负责 supervisor 范式相关的上下文组装和模型调用输入构造。

职责划分调整为：

- `SupervisorContextFactory`
  - `TaskRequest -> supervisor conversation state`
  - `conversation state + worker results + worker registry -> SupervisorContext`
  - `SupervisorContext -> routing ModelInvocationContext`
  - `worker dispatch -> worker execution AgentRunContext`
  - `worker result -> next supervisor conversation state`
  - candidate / worker result / fallback instruction 等与上下文渲染强相关的逻辑
- `SupervisorOrchestrator`
  - 只负责循环、worker runtime 调用、事件发布、终止条件
- `HybridSupervisorAgent`
  - 只负责基于 `SupervisorContext` 做决策
  - 保留 reviewer pass、no-progress、candidate 选择、最终 `SupervisorDecision` 产出
  - 不再负责渲染 routing model 输入

## Why One Factory

有两个可选拆法：

1. 一个 `SupervisorContextFactory` 同时服务 orchestrator 和 agent
2. 再拆一个 `SupervisorWorkerContextFactory`

这里选择前者，因为当前 supervisor 上下文的核心问题是“职责四散”，不是“工厂过大”。只要把所有上下文与 invocation 相关逻辑收拢到一个地方，就已经能显著降低耦合，而且能与现有 `PlanningAgentContextFactory`、`ReflexionCriticContextFactory` 的使用方式保持一致。

## Proposed API

`SupervisorContextFactory` 实现 `AgentContextFactory`，并扩展 supervisor 专用方法。

建议提供这些方法：

- `prepareInitialContext(TaskRequest request)`
  - 初始化 supervisor conversation state
  - 保留 session memory
  - `currentContent` 取任务文档内容
- `buildSupervisorContext(TaskRequest request, AgentRunContext conversationState, List<SupervisorContext.WorkerResult> workerResults, List<SupervisorContext.WorkerDefinition> availableWorkers)`
  - 生成传给 `SupervisorAgent.decide(...)` 的 `SupervisorContext`
  - 这里统一做 snapshot copy，避免历史 context 被后续 append 污染
- `buildRoutingInvocationContext(SupervisorContext context, List<SupervisorContext.WorkerDefinition> candidates)`
  - 构造 routing 模型调用输入
  - 负责渲染：
    - instruction
    - current content
    - candidate workers
    - previous worker results
- `buildWorkerExecutionContext(AgentRunContext conversationState, String currentContent)`
  - 构造子 worker 的运行态上下文
  - 只共享结构化 supervisor 摘要 memory，不共享工具 transcript
- `summarizeWorkerResult(AgentRunContext conversationState, String workerId, ExecutionResult<?> result)`
  - 追加 `Previous worker result` 摘要
  - 更新 current content
  - 返回下一轮 supervisor conversation state
- `buildFallbackInstruction(SupervisorContext.WorkerDefinition worker, SupervisorContext context)`
  - 构造 rule-based fallback instruction

`buildModelInvocationContext(AgentRunContext context)` 在这个 factory 中不是主入口，但为了满足 `AgentContextFactory` 顶层接口，仍然保留实现，可对非 `SupervisorContext` 直接抛出异常或代理到 `buildRoutingInvocationContext(...)` 的更明确入口。

## Data Flow

重构后的运行链路如下：

1. `SupervisorOrchestrator.execute(request)`
2. `supervisorContextFactory.prepareInitialContext(request)` 生成初始 conversation state
3. 每轮循环：
   - `supervisorContextFactory.buildSupervisorContext(...)`
   - `supervisorAgent.decide(supervisorContext)`
4. 若 `AssignWorker`
   - `supervisorContextFactory.buildWorkerExecutionContext(...)`
   - `executionRuntime.run(...)`
   - `supervisorContextFactory.summarizeWorkerResult(...)`
   - 累加 `SupervisorContext.WorkerResult`
5. 若 `Complete`
   - orchestrator 发布完成事件并返回 `TaskResult`

对 `HybridSupervisorAgent` 而言：

1. 基于 `SupervisorContext` 做 reviewer/no-progress/candidate 规则判断
2. 若需要模型辅助路由：
   - 调 `supervisorContextFactory.buildRoutingInvocationContext(...)`
   - 使用 routing model
3. 基于模型输出和候选集合决定 `AssignWorker` 或 `Complete`

## Behavior Rules To Keep

这次重构不改变 supervisor 的业务行为，只做职责迁移。必须保留：

- reviewer `PASS + instructionSatisfied + evidenceGrounded` 时直接 complete
- 连续两次无进展时 complete
- 连续两次同 worker 时下一轮 demote 该 worker
- reviewer 指出 grounding 问题时优先 research / write
- reviewer 指出 instruction gap 时优先 write
- 模型选出候选集合外 worker 时回退到 rule-based fallback
- worker 之间只共享摘要，不共享完整工具 transcript

## Testing Strategy

测试分三层：

- `SupervisorContextFactoryTest`
  - 初始 context 组装
  - `SupervisorContext` snapshot 行为
  - worker execution context 不泄露工具 transcript
  - worker result summary 写回行为
  - routing invocation message 渲染
- `SupervisorOrchestratorTest`
  - 改为断言 orchestrator 通过 factory 组装上下文
  - 保持现有 heterogeneous workers / repeated worker / memory isolation 用例
- `HybridSupervisorAgentTest`
  - 保持现有决策规则用例
  - 增加对 factory 生成 routing invocation 输入的协作断言

## Files Likely Affected

- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`

## Risks

- `AgentContextFactory` 当前接口较薄，supervisor 需要额外专用方法；命名要清晰，避免变成“万能工具类”
- `HybridSupervisorAgent` 现有 routing model 调用方式比较轻，如果强行改成通用 `buildModelInvocationContext(AgentRunContext)` 可能会让类型语义变差，因此应保留 `buildRoutingInvocationContext(SupervisorContext, candidates)` 这样的 supervisor 专用入口
- `SupervisorContext` 的 `availableWorkers` / `workerResults` 必须继续保持 snapshot 语义，否则历史 context 会被后续循环污染

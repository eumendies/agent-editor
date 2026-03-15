# Hybrid Supervisor Design

**Date:** 2026-03-15

## Goal

新增一个比 [`SequentialSupervisorAgentDefinition.java`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/supervisor/SequentialSupervisorAgentDefinition.java) 更接近生产用法的 supervisor 实现，在保持当前 `SupervisorOrchestrator` 主体结构不变的前提下，实现：

- 多轮动态 worker 调度
- 基于语义的候选 worker 筛选
- 允许重复调度同一个 worker
- LLM 可主动结束任务
- 明确的防循环和强制收口兜底

## Non-Goals

- 不重写 `SupervisorOrchestrator` 为并行调度器
- 不改造 worker 执行 runtime
- 不引入新的 agent 模式
- 不做通用能力发现平台，只做最小可用的 worker 路由元数据

## Chosen Approach

本次采用“混合式 supervisor”。

- 规则层负责候选 worker 筛选和防循环约束
- LLM 只在候选集合中做受限选择，或者返回完成
- orchestrator 继续负责执行、事件和 trace

之所以不继续使用纯规则方案，是因为多轮任务在编辑、分析、复核之间切换时会很快变成硬编码。之所以不采用纯 LLM supervisor，是因为当前 worker 池小、行为边界明确，更适合先用受限决策保持可预测性和可测试性。

## Core Design

### 1. Keep the Interface, Add a New Implementation

保留 [`SupervisorAgentDefinition.java`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorAgentDefinition.java) 作为接口。

新增实现类：

- `HybridSupervisorAgentDefinition`

Spring 配置从注入 `SequentialSupervisorAgentDefinition` 切换为注入 `HybridSupervisorAgentDefinition`。旧顺序实现可暂时保留作为 fallback 参考，不再作为默认 bean。

### 2. Extend Worker Metadata Minimally

扩展 [`WorkerDefinition.java`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/supervisor/WorkerDefinition.java)，新增最小路由元数据：

- `capabilities`

第一版能力标签只定义当前真正需要的语义：

- `analyze`
- `edit`
- `review`

配置层的三个 worker 对应关系为：

- `analyzer` -> `analyze`
- `editor` -> `edit`
- `reviewer` -> `review`

这样 supervisor 的规则层可以不依赖脆弱的字符串角色名做路由。

### 3. Supervisor Decision Contract

现有 [`SupervisorDecision.java`](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorDecision.java) 可以继续复用：

- `AssignWorker`
- `Complete`

不需要扩展新的外部决策类型。LLM 内部输出可以先解析为一个私有中间结构，再转换成现有 `SupervisorDecision`，从而把改动限制在新实现类内部。

## Decision Flow

每轮 `decide(...)` 的处理流程固定为：

1. 基于 `SupervisorContext` 收集：
   - 原始指令
   - 当前文档内容
   - 可用 worker 列表
   - 历史 `workerResults`
2. 规则层计算候选 worker：
   - 根据用户指令命中能力标签
   - 根据最近若干轮已执行 worker 做去重或降权
   - 当某类能力尚未被触发过时优先纳入候选
3. 如果候选为空，直接返回 `Complete`
4. 将候选 worker、当前内容摘要和最近执行历史交给 LLM
5. LLM 只能返回：
   - `assign_worker`
   - `complete`
6. 如果 LLM 返回非法 worker、非法动作或无法解析：
   - 先走一次规则兜底
   - 如果连续非法超阈值，则直接结束
7. orchestrator 执行所选 worker，并把结果写回上下文

## Prompt And Output Shape

新 supervisor 的 prompt 只描述受限决策问题，不承担 worker 执行本身。

Prompt 内容包括：

- 任务原始 instruction
- 当前文档内容
- 最近几轮 worker 执行摘要
- 候选 worker 列表
- 明确结束条件提醒
- 明确输出格式约束

模型输出采用结构化 JSON，第一版字段最小化为：

```json
{
  "action": "assign_worker",
  "workerId": "editor",
  "instruction": "Apply the edits needed to make the document concise.",
  "reasoning": "analysis already identified style issues"
}
```

或：

```json
{
  "action": "complete",
  "summary": "analysis and editing are complete",
  "reasoning": "review found no remaining issues"
}
```

JSON 结构要显式限制在候选 worker 范围内，避免模型虚构 worker id。

## Candidate Worker Rules

规则层不直接做最终决策，但负责把 LLM 的搜索空间收窄到“合理候选”。

第一版规则：

- 指令命中“检查、分析、找问题、总结现状”时优先 `analyze`
- 指令命中“修改、润色、重写、格式化、改正文档”时优先 `edit`
- 指令命中“复查、审核、review、确认是否还有问题”时优先 `review`
- 没有明确命中时：
  - 首轮优先 `analyze`
  - 中间轮优先尚未执行过的能力
  - 所有能力都执行过后仍可重复，但要考虑最近执行历史降权
- 同一 worker 允许重复，但连续重复要受限

这组规则的目标不是替代模型，而是让模型在合理边界内工作。

## Termination And Loop Control

终止条件分为软终止和硬终止。

### Soft Termination

LLM 可以主动返回 `complete`。

这适用于：

- 任务目标已经达成
- 当前 worker 结果已经足够收口
- 再继续调度不会带来明显新信息

### Hard Termination

规则层必须提供兜底：

- 达到 supervisor 最大轮数，例如 `6` 或 `8`
- 候选 worker 为空
- 同一 worker 连续多次被选中超过阈值
- 连续若干轮没有内容变化且结果摘要没有新信息
- LLM 连续输出非法决策超过阈值

当命中硬终止时，supervisor 返回 `Complete`，summary 基于历史 `workerResults` 自动生成，保证流程总能稳定收口。

## Observability

第一版不新增新的外部接口，但要补齐 trace，使调度可解释。

推荐新增或增强的 trace 内容：

- 候选 worker 列表
- 规则筛选原因
- LLM 原始 supervisor 决策文本
- 解析后的动作
- 非法输出兜底原因
- 终止原因

这些记录应继续走现有 `TraceCollector`，分类仍归在编排相关 trace 下，避免再开新子系统。

## Testing Strategy

优先做 TDD，测试从“决策器”开始，而不是先改配置。

需要新增或调整的测试分层：

1. `HybridSupervisorAgentDefinitionTest`
- 候选 worker 过滤正确
- LLM 选择合法 worker 时返回 `AssignWorker`
- LLM 返回 `complete` 时正确收口
- LLM 返回非法 worker 时走规则兜底
- 连续重复 worker 或无进展时触发结束

2. `SupervisorOrchestratorTest`
- 验证 orchestrator 与新 supervisor 集成后仍能完成多轮调度
- 验证允许重复 worker 时仍能在预算内收口

3. `AgentV2ConfigurationSplitTest`
- 验证默认注入的 supervisor bean 已切到新实现
- 验证 `WorkerRegistry` 中 worker capability 元数据已存在

## Risks

### 1. Prompt Too Loose

如果 supervisor prompt 不够约束，模型可能反复选择同一个 worker 或给出空泛 instruction。解决办法是：

- 只暴露候选 worker
- 强制 JSON 输出
- 在 prompt 中明确“不允许重复无意义调度”

### 2. Rule Layer Too Weak

如果规则层只做 very light filtering，LLM 仍然可能在低质量候选里打转。第一版至少要把：

- 首轮能力优先级
- 最近执行历史降权
- 非法输出兜底

做完整。

### 3. Over-Engineering Worker Metadata

能力标签如果一开始设计过细，会给配置和测试带来不必要成本。第一版只保留 `capabilities`，不扩展更复杂的 `preferredTasks` 或 `repeatable` 字段。

## Summary

本次设计的核心，是把 supervisor 从“顺序跑完全部 worker”升级为“规则限界下的 LLM 动态调度器”：

- 保留现有 orchestrator 主干
- 增加最小 worker 语义元数据
- 用规则控制候选和防循环
- 用 LLM 提升多轮分派灵活性
- 保持收口可预测、可测试、可追踪

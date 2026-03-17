# Agent V2 Reflexion Design

**Date:** 2026-03-17

## Goal

为 `agent.v2` 新增一种独立的顶层 `Reflexion Agent`，采用固定的双角色自协作流程：

- actor 负责产出和修改文档
- critic 负责分析结果并给出 `PASS / REVISE`
- orchestrator 负责循环推进，直到 critic 通过或达到最大轮数

这不是对 `ReactAgentDefinition` 的 prompt 增强，也不是 `Supervisor` 的一个变体，而是一种新的顶层 orchestration 模式。

## Chosen Scope

本次设计明确采用以下约束：

- 新增顶层 `AgentType.REFLEXION`
- 使用新的 `ReflexionOrchestrator`
- 不复用 `SupervisorAgentConfig` 里的 `reviewer`
- critic 可以调用分析类工具，但不能修改文档
- 停止条件以 critic 的 `PASS / REVISE` 为准

## Why This Should Not Reuse Supervisor

虽然 `Supervisor` 也具备多角色编排能力，但它的语义是“候选 worker 路由”，而 `Reflexion` 的语义是“固定的 actor/critic 自反馈回路”。

两者在以下方面不同：

- 角色数量固定
- 控制流固定
- critic 的输出是 structured critique，而不是普通 worker summary
- 停止条件由 critique verdict 驱动，而不是 supervisor 自主选择 complete

因此 `Reflexion` 更适合独立成为一个顶层 `TaskOrchestrator`。

## Recommended Architecture

新增下列核心组件：

- `AgentType.REFLEXION`
- `ReflexionOrchestrator`
- `ReflexionActorDefinition`
- `ReflexionCriticDefinition`
- `ReflexionCritique`
- `ReflexionVerdict`

职责边界：

- `ReflexionOrchestrator`
  - 持有 actor/critic 循环
  - 维护 actor 的 `ExecutionState`
  - 每轮决定是否结束或继续

- `ReflexionActorDefinition`
  - 负责根据原始 instruction 和历史 critique 产出文档
  - 可调用编辑类工具

- `ReflexionCriticDefinition`
  - 负责审查 actor 当前产物
  - 返回结构化 `PASS / REVISE`
  - 可调用分析类工具，但不可编辑文档

- `ExecutionRuntime`
  - 继续只负责单 agent 执行
  - 不理解 reflexion 语义

## Core Flow

每轮 Reflexion 流程固定为：

1. actor 执行
2. critic 执行
3. orchestrator 读取 critique
4. 若 `PASS`，结束
5. 若 `REVISE`，将 critique 追加到 actor memory，进入下一轮

其中：

- actor state 是跨轮复用的
- critic state 每轮 fresh
- 文档真实状态始终由 actor 的 `ExecutionState.currentContent` 承载

## Data Model

### 1. ReflexionVerdict

建议定义为：

```java
public enum ReflexionVerdict {
    PASS,
    REVISE
}
```

### 2. ReflexionCritique

建议定义为：

```java
public record ReflexionCritique(
        ReflexionVerdict verdict,
        String feedback,
        String reasoning
) {
}
```

语义：

- `verdict`
  - critic 的判定
- `feedback`
  - 给 actor 的修订意见
- `reasoning`
  - 供 trace/debug 使用的解释

## Actor Input Strategy

actor 每轮输入由三部分组成：

- 当前文档内容
- 原始 instruction
- 历史 critique 摘要

推荐把 critique 以 `ExecutionMessage.UserExecutionMessage` 形式追加到 actor memory，例如：

```text
Critique round 1:
- The introduction is too long.
- Tighten the conclusion and remove repetition.
```

这样 actor 依旧可以复用现有基于 transcript memory 的 message 构造方式。

## Critic Input Strategy

critic 每轮输入应至少包含：

- 原始 instruction
- actor 当前产出的文档
- 当前轮 actor summary 或最终 message
- 可选的历史 critique

critic 的输出必须是结构化 verdict，而不是自由文本。

因此 critic 更适合使用专门的 AiService，例如：

- `ReflexionCriticAiService`

而不是直接复用 `ReactAgentDefinition`。

## Tool Boundaries

### Actor Allowed Tools

推荐第一版允许：

- `editDocument`
- `searchContent`

可选是否允许 `analyzeDocument`，但为了角色边界更清楚，第一版不建议加入。

### Critic Allowed Tools

推荐第一版允许：

- `searchContent`
- `analyzeDocument`

明确不允许：

- `editDocument`

这保证 critic 只能分析和审查，而不会直接越权修改文档。

## Stop Conditions

建议固定以下结束策略：

1. critic 返回 `PASS`
   - 直接结束，返回 actor 最新文档

2. 达到 `maxReflectionRounds`
   - 强制结束，返回 actor 最新文档
   - trace 中记录 `max rounds reached`

3. actor/critic 运行异常
   - 直接失败
   - 不吞异常，不 silently fallback

第一版不建议：

- 让 actor 忽略 critique 后主动结束
- 让 critic 返回非法 verdict 时自动降级为 `REVISE`

这些行为会使流程不稳定且难以调试。

## Configuration

推荐新增独立配置：

- `ReflexionAgentConfig`

负责注册：

- `ReflexionActorDefinition`
- `ReflexionCriticDefinition`
- `ReflexionOrchestrator`

并在 routing 层把 `AgentType.REFLEXION` 映射到新的 orchestrator。

这样它不会和现有：

- `ReactAgentConfig`
- `SupervisorAgentConfig`

互相污染。

## Trace Direction

建议在现有 trace 体系上补充 reflexion-specific 决策节点：

- `reflexion.actor.started`
- `reflexion.actor.completed`
- `reflexion.critic.started`
- `reflexion.critic.completed`
- `reflexion.revise`
- `reflexion.pass`
- `reflexion.max.rounds.reached`

这样后续排查会很直接。

## Testing Strategy

至少覆盖：

1. `ReflexionOrchestratorTest`
   - actor -> critic -> pass
   - actor -> critic -> revise -> actor retry -> pass
   - max rounds reached
   - critic tool boundary

2. `ReflexionCriticDefinitionTest`
   - 正确映射 `PASS / REVISE`
   - 非法响应处理

3. routing/config 测试
   - `AgentType.REFLEXION` 被正确路由到新的 orchestrator

## Non-Goals

第一版不做：

- 动态 actor/critic 角色注册
- critic 直接编辑文档
- 复用 supervisor 的 worker registry
- 多 critic 投票
- critic checkpoint 跨轮复用

## Summary

这次 Reflexion 设计的关键点是：

- 它是新的顶层 orchestration 模式
- actor 与 critic 角色固定
- actor state 跨轮复用，critic state 每轮 fresh
- critique 以 structured verdict 驱动循环推进
- critic 只分析，不编辑

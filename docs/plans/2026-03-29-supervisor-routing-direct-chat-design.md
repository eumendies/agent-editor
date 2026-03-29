# Supervisor Routing Direct Chat Design

## Goal

移除 `SupervisorRoutingAiService` 对 LangChain4j `AiService` 的依赖，让 `HybridSupervisorAgent` 像 `ReactAgent` 一样直接发起 `ChatModel` 请求，同时把 supervisor routing 的上下文工程收敛到 `SupervisorContextFactory`。

## Confirmed Scope

这次改造只替换模型调用路径，不改变 `HybridSupervisorAgent` 的外部行为：

- 保留 `SupervisorRoutingResponse` 作为结构化路由协议
- 保留候选 worker 合法性校验
- 保留 reviewer 通过即完成、连续无进展即完成、重复 worker 降级、fallback 回退等既有决策规则
- 保留 Spring 配置入口不变，外部仍通过 `HybridSupervisorAgent` 使用 supervisor 路由

## Problem

当前 `HybridSupervisorAgent` 通过 `SupervisorRoutingAiService` 调用模型。这个方式对简单结构化输出足够，但存在两个问题：

- 上下文工程不便扩展。`AiService` 把 prompt 约束写在注解里，system prompt、user prompt、响应格式约束都分散在接口定义中，不利于后续继续做 supervisor routing 的上下文演进。
- `HybridSupervisorAgent` 无法像 `ReactAgent` 一样统一走 `ContextFactory -> ModelInvocationContext -> ChatRequest` 的调用路径，导致 agent v2 里不同 agent 的模型调用方式不一致。

## Chosen Approach

采用直接请求模式：

- `HybridSupervisorAgent` 持有 `ChatModel`
- `SupervisorContextFactory` 负责构造 routing 所需的 `ModelInvocationContext`
- `HybridSupervisorAgent` 使用该上下文构建 `ChatRequest`，调用 `chatModel.chat(...)`
- agent 再把 `AiMessage.text()` 解析为 `SupervisorRoutingResponse`

不再保留单独的 `SupervisorRoutingAiService` 接口。

## Context Design

`SupervisorContextFactory.buildRoutingInvocationContext(...)` 负责生成 supervisor routing 的全部模型输入，消息形态固定为：

1. `SystemMessage`
   - 说明 supervisor routing 的职责
   - 约束允许动作只能为 `assign_worker` 或 `complete`
   - 约束 `workerId` 必须来自候选集合
   - 约束 `assign_worker` 必须带 `instruction`
   - 约束 `complete` 必须带 `summary` 和 `finalContent`
   - 明确要求模型输出 JSON
2. `UserMessage`
   - 原始任务 instruction
   - 当前 document content
   - 候选 workers
3. 历史 `WorkerResult`
   - 每一条历史结果单独转换成一条 `AiMessage`
   - 让模型把它们视为已经发生过的 worker 执行轨迹，而不是新的用户要求

这样既避免把所有内容揉成一条大 prompt，也把上下文组织权收敛到 factory。

## Structured Output

`SupervisorContextFactory` 还负责提供 routing 调用使用的 `responseFormat`，目标是尽量约束模型返回 `SupervisorRoutingResponse` 对应的 JSON 文本。`HybridSupervisorAgent` 不再关心 schema 文案，只负责消费 `ModelInvocationContext`。

## Error Handling

这次改造保留现有容错策略：

- `chatModel` 为空时不发起模型路由，直接走 fallback
- 模型调用抛异常时直接走 fallback
- 模型返回空文本或 JSON 解析失败时直接走 fallback
- 模型选择候选集合外 worker 时视为非法输出，走 fallback

这些失败都不能打断 supervisor 主流程。

## Testing Strategy

测试按 TDD 调整：

- `SupervisorContextFactoryTest`
  - 验证 routing invocation 现在会产出 `SystemMessage + UserMessage + 多条 AiMessage`
  - 验证 user prompt 中包含任务、当前正文、候选 workers
  - 验证每个 `WorkerResult` 会独立映射为一条 `AiMessage`
  - 验证 `responseFormat` 已配置
- `HybridSupervisorAgentTest`
  - 去掉 `AiService` 依赖，改为 stub `ChatModel`
  - 验证直接请求路径仍能得到与原先一致的决策结果
  - 验证 agent 会消费 factory 生成的 invocation context
- 配置测试
  - 验证 `HybridSupervisorAgent` 继续由 Spring 正常注入 `ChatModel` 与 `SupervisorContextFactory`

## Files To Change

- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/SupervisorRoutingAiService.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

## Risks

- `responseFormat` 的具体构造需要与当前项目里 LangChain4j 的使用方式保持一致，否则可能出现模型能返回文本但无法稳定满足结构化输出约束的问题。
- `AiMessage` 用于承载历史 `WorkerResult` 时，需要保持内容格式足够稳定，否则测试容易只验证“有消息”而没有验证消息语义。
- 删除 `SupervisorRoutingAiService` 后，相关测试和构造函数需要一起收敛，否则容易留下只在测试里使用的过时入口。

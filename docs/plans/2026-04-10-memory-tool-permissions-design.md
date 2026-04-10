# Memory Tool Permissions Design

## Context

长期记忆当前只在 `MEMORY` worker 下暴露写入工具：

- `MAIN_WRITE` 只可见 `searchMemory`
- `MEMORY` 可见 `searchMemory` 和 `upsertMemory`
- `REVIEW` / `RESEARCH` 都不可见 memory 工具

这导致单 agent ReAct、planning 执行阶段、reflexion actor 以及 supervisor writer 在发现稳定文档决策时无法写入 `DOCUMENT_DECISION`。同时 reviewer / critic 在校验输出是否违反既有文档约束时，也无法主动读取长期文档决策。

`MemoryUpsertTool` 已经在执行层限制自主写入只能目标为 `DOCUMENT_DECISION`，不会允许 agent 写 `USER_PROFILE`。用户画像仍然通过 UI/API 确认链路维护。

## Goal

调整长期记忆工具权限，使写作类 agent 可以维护文档级长期记忆，评审类 agent 可以读取文档级长期记忆，但不扩大用户画像写入权限。

## Permission Matrix

新的 memory 工具权限矩阵：

| Execution role | searchMemory | upsertMemory | Reason |
| --- | --- | --- | --- |
| `MAIN_WRITE` | yes | yes | 写作阶段最容易发现可复用文档决策，也需要写入 durable `DOCUMENT_DECISION` |
| `MEMORY` | yes | yes | 专用 memory worker 继续拥有完整文档决策维护能力 |
| `REVIEW` | yes | no | reviewer / critic 需要读取既有文档约束来判定是否违反，但不应写入 |
| `RESEARCH` | no | no | research 只负责知识库取证，不混入文档长期决策 |

## Prompt Rules

仅调整工具可见性不够。相关 system prompt 需要告诉模型什么时候使用长期记忆工具。

### Write roles

`ReactAgentContextFactory` 和 `GroundedWriterAgentContextFactory` 增加 memory rules：

- 使用 `searchMemory` 查询可能影响当前任务的 prior document decisions
- 只有发现稳定、可复用、未来编辑应继续遵守的文档约束时，才使用 `upsertMemory`
- 自主写入只能是 `DOCUMENT_DECISION`
- 不写执行日志、一次性编辑、临时计划或 `USER_PROFILE`
- 已有记忆过期时优先 replace/delete，避免重复 create

### Review roles

`EvidenceReviewerAgentContextFactory` 和 `ReflexionCriticContextFactory` 增加 memory rules：

- 使用 `searchMemory` 检查 prior document decisions
- 将检索到的文档决策作为评审约束
- 不写 memory

### Memory worker

`MemoryAgentContextFactory` 已经限定只管理 `DOCUMENT_DECISION`，无需改变权限语义。可以只做少量文案对齐。

## Non-Goals

- 不允许 agent 自主写 `USER_PROFILE`
- 不引入新的 execution role
- 不拆出复杂 capability matrix
- 不改变 `MemoryUpsertTool` 的执行层安全约束
- 不改变长期记忆 schema 或 repository 语义

## Testing

本次以 TDD 推进：

1. 更新 `MemoryToolAccessPolicyTest`
   - `MAIN_WRITE` 期望包含 `searchMemory + upsertMemory`
   - `REVIEW` 期望包含 `searchMemory`
   - `RESEARCH` 仍为空

2. 更新 `ExecutionToolAccessPolicyTest`
   - `MAIN_WRITE` 组合结果包含文档工具和两个 memory 工具
   - `REVIEW` 组合结果包含 review 文档工具和 `searchMemory`

3. 更新 orchestrator 相关测试
   - ReAct / planning / reflexion actor 允许工具列表加入 `upsertMemory`
   - supervisor writer 允许工具列表加入 `upsertMemory`
   - review / critic 允许工具列表加入 `searchMemory`

4. 更新 prompt 测试
   - write prompt 包含 `searchMemory` / `upsertMemory` 的使用规则
   - review prompt 包含 `searchMemory` 的读取规则但不包含写入规则

5. 运行全量 `mvn test`

## Risks

### 模型过度写入

开放 `MAIN_WRITE` 写入后，模型可能倾向于频繁 create。通过 prompt 规则和 `MemoryUpsertTool` 的 `DOCUMENT_DECISION` 限制降低风险。后续如果仍然过度写入，再考虑增加写入前确认或更严格的工具参数校验。

### reviewer 混淆知识库和记忆

`REVIEW` 只开放 `searchMemory`，不开放 knowledge retrieval。它读取的是已确认文档决策，不是外部知识证据，因此角色边界仍然清晰。

### supervisor memory worker 重叠

`MAIN_WRITE` 和 `MEMORY` 都能写文档决策，但职责不同：writer 在产生决策时顺手沉淀，memory worker 专门整理约束。短期允许重叠，靠 replace/delete 指南减少重复。

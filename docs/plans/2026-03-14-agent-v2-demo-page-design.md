# Agent V2 Demo Page Design

## Goal

把当前单一编辑器风格的首页改成更适合演示的流程驾驶舱，重点展示 `REACT / PLANNING / SUPERVISOR` 三种模式的差异，并且继续复用现有后端接口和 WebSocket 事件。

## Chosen Direction

采用“对比驾驶舱”方案，而不是继续强化传统编辑器界面。

页面会围绕同一个任务的三种编排策略展开：

- `ReAct`：单 agent 即时决策
- `Planning`：先拆计划再执行
- `Supervisor`：supervisor 串行调度多个异构 worker

## Structure

页面拆成三段：

1. `Demo Hero`
展示标题、连接状态和页面定位，让用户一眼看出这是编排系统演示页而不是普通编辑器。

2. `Scenario Bar`
统一输入任务说明、选择模式、触发执行。这里强调“同一任务，不同策略”的对比关系。

3. `Comparison Stage`
展示原始文档、当前结果、diff，以及执行事件流。事件流需要对不同模式做视觉分层，尤其突出 planning 和 supervisor 的关键节点。

## Interaction

不新增后端接口，继续复用：

- `POST /api/v1/agent/execute`
- `GET /api/v1/agent/task/{taskId}/steps`
- WebSocket task updates

页面会增加模式说明卡和更清晰的事件视觉分层，但不会引入新的前端框架。

## Visual Direction

视觉风格从“IDE 暗色编辑器”切换到“深色驾驶舱”：

- 更强标题区
- 更明显的模式色彩区分
- 更像 pipeline timeline 的步骤流
- 更适合现场讲解的说明文字

## Implementation Boundary

- 只改 `src/main/resources/templates/index.html`
- 必要时只补最小页面烟雾测试
- 不改 controller API 语义
- 不额外拆前端工程结构

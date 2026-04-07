# User Profile Memory UI Design

## Goal

在现有 demo/workbench 页面中显式展示 `USER_PROFILE` 长期记忆，让用户可以查看、创建、编辑、删除自己的 profile 条目，和“用户手写 user profile、AI 只消费不自动写入”的长期记忆目标保持一致。

## Context

当前后端已经具备完整的 user profile CRUD 能力：

- `GET /api/v2/memory/profiles`
- `POST /api/v2/memory/profiles`
- `PUT /api/v2/memory/profiles/{memoryId}`
- `DELETE /api/v2/memory/profiles/{memoryId}`

这些接口由 [LongTermMemoryController.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/controller/LongTermMemoryController.java) 和 [TaskApplicationService.java](/Users/eumendies/code/java/learn/agent-editor/src/main/java/com/agent/editor/service/TaskApplicationService.java) 提供。

缺口只在前端：[index.html](/Users/eumendies/code/java/learn/agent-editor/src/main/resources/templates/index.html) 目前只有文档编辑区、聊天区和 knowledge upload 区，没有任何 user profile memory 的可视化入口，因此用户既无法确认当前持久化了哪些 profile，也无法主动维护它们。

## Requirements

### Functional

- 用户能看到当前所有已持久化的 `USER_PROFILE` 条目。
- 用户能新增一条 profile。
- 用户能编辑已有 profile 的文本内容。
- 用户能删除已有 profile。
- 用户操作完成后，界面应立即刷新为最新服务端状态。
- 所有操作都应直接复用现有 `/api/v2/memory/profiles` 接口，不新增后端协议。

### Non-Functional

- 不改变现有 agent 执行协议。
- 不改变 user profile 注入后端任务的方式；仍由后端在任务启动时统一加载并拼装为 `userProfileGuidance`。
- UI 风格应延续现有 workbench 页面，不单独引入新的页面或前端框架。
- 只处理 `USER_PROFILE`，不在本轮引入 `DOCUMENT_DECISION` 的前端管理入口。

## Approaches Considered

### Option 1: 在右侧聊天区下方增加固定面板

这是推荐方案。

优点：

- user profile 和对话最相关，放在聊天区附近最容易让用户理解“这些偏好正在影响 agent 输出”。
- 不需要额外路由或弹层，信息是显式可见的。
- 对现有页面结构改动较小，只是在右侧面板里增加一个新 section。

缺点：

- 右侧面板会更长，需要控制滚动和高度。

### Option 2: 放到底部 knowledge 区旁边

优点：

- 都属于长期上下文，信息架构上比较集中。

缺点：

- 离主聊天流程较远，用户不容易将其和当前 agent 行为建立直接联系。

### Option 3: Drawer / Modal

优点：

- 主页面更干净。

缺点：

- “显式暴露”目标达成较差。
- 查看和编辑都要额外一步操作，降低了可维护性。

## Chosen Design

采用 Option 1：在右侧聊天区下方新增固定的 `User Profile Memory` 面板。

### Layout

右侧区域从当前的三段结构：

- runtime status
- message list
- composer

扩展为四段结构：

- runtime status
- message list
- composer
- user profile memory panel

`message list` 继续作为主滚动区域；`User Profile Memory` 面板保持独立容器，避免和消息流混在一起。

### Panel Contents

面板包含：

- 标题与说明文案
  - 明确这些条目会被作为长期用户偏好注入后续任务。
- 顶部新增区
  - 一个 textarea
  - 一个 `Add Profile` 按钮
- 当前 profile 列表
  - 每条卡片展示 `summary`
  - `Edit` 按钮
  - `Delete` 按钮
- 面板内状态提示区域
  - 用于显示 loading / success / error

### Editing Model

采用行内编辑，而不是弹窗。

原因：

- 实现简单，和当前页面原生 JS 风格一致。
- 编辑对象就是一段文本，不需要复杂表单。
- 用户能明确看到自己正在改哪一条 profile。

编辑态下，单条卡片切换为：

- textarea
- `Save`
- `Cancel`

### Data Flow

- 页面初始化时调用 `loadUserProfiles()`。
- 新增：`POST /api/v2/memory/profiles`
- 编辑：`PUT /api/v2/memory/profiles/{memoryId}`
- 删除：`DELETE /api/v2/memory/profiles/{memoryId}`
- 每次成功操作后重新调用 `loadUserProfiles()`，以服务端状态为准。

这种做法比在前端本地做复杂 state merge 更稳，尤其适合当前页面这种原生 JS demo 结构。

## Frontend State

页面新增最小状态：

- `userProfiles`
- `userProfilesLoading`
- `userProfileStatus`
- `editingProfileId`
- `editingProfileDraft`

这些状态只服务当前面板，不影响文档编辑或任务执行状态。

## Error Handling

- 列表加载失败：面板展示错误文案，但不影响页面其他功能。
- 新增/编辑失败：保留当前输入内容，提示失败原因。
- 删除失败：保留当前列表，提示失败原因。
- 接口返回空列表：显示空态说明，而不是空白区域。

## Testing Strategy

### Backend

本轮不新增后端接口；重点是保证前端继续复用现有接口，因此后端只需要保持已有测试覆盖。

### Frontend / Template

当前仓库没有独立的浏览器自动化测试体系，因此本轮以以下方式验证：

- 模板结构和脚本逻辑的 focused review
- 后端相关 controller/service 测试不回归
- 手工验证清单：
  - 页面加载后能看到已有 user profile
  - 新增成功后列表刷新
  - 编辑成功后文本更新
  - 删除成功后列表消失
  - 失败时状态文案正确显示

## Out of Scope

以下内容不在本轮实现范围内：

- `DOCUMENT_DECISION` 的前端管理界面
- user profile 的标签、排序、分组
- 富文本编辑
- 多页面 memory 管理中心
- 在聊天消息流中实时展示当前 user profile guidance 的最终拼装文本

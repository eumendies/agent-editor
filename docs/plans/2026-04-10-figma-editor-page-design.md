# Figma Editor Page Design

## Goal

用纯原生 HTML5 和 CSS3 将首页还原为 Figma 中的极简浅色 AI 文档编辑器，同时保留并重新接入现有后端交互。

## Confirmed Approach

采用最大化视觉还原方案：重写 `src/main/resources/templates/index.html` 的页面结构和样式，使首屏接近设计图中的桌面工作台，而不是沿用现有深色卡片风格。

后端协议不变，页面仍通过原生 JavaScript 调用现有接口：

- `GET /api/v1/documents`
- `GET /api/v1/documents/{documentId}`
- `PUT /api/v1/documents/{documentId}`
- `GET /api/v1/diff/document/{documentId}/pending`
- `POST /api/v1/diff/document/{documentId}/apply`
- `DELETE /api/v1/diff/document/{documentId}/pending`
- `GET /api/v1/diff/document/{documentId}`
- `POST /api/agent/execute`
- `GET /api/agent/task/{taskId}`
- `POST /api/v1/knowledge/documents`
- `GET /api/memory/profiles`
- `POST /api/memory/profiles`
- `PUT /api/memory/profiles/{memoryId}`
- `DELETE /api/memory/profiles/{memoryId}`
- `WS /ws/agent`

## Layout

页面分为三层：

1. 深色顶部栏：左侧 Figma/AI 图标，中间标题 `AI Document Editor UI Design`，右侧状态图标、分享按钮和用户信息。
2. 主体 Flexbox 双栏：左侧文档区弹性占满剩余空间，右侧对话区固定宽度。
3. 右侧底部 composer：输入框内包含 agent mode 下拉框和发送按钮。

左侧工作区包含：

- 顶部工具条：文档下拉、knowledge file 选择、上传按钮。
- 主输入区：大面积文档 textarea，模拟 Figma 中无边框正文编辑面。
- 不可编辑 diff 区：浅灰背景、等宽字体、红绿内联 diff。
- 底部 user profile 面板：新增输入框、Add 按钮、已保存 profile 列表和状态提示。

右侧对话区包含：

- 标题行：`AI Assistant`、WebSocket 状态、消息数、用户名称。
- 消息列表：系统消息靠左、用户消息深色靠右、assistant 消息浅色靠左。
- 底部输入框：左侧 mode 下拉，中间文本输入，右侧发送按钮。

## Visual Rules

- 使用 Flexbox 实现左右分栏。
- 背景以 `#f7f8fa`、`#f3f4f6`、`#ffffff` 为主。
- 边框使用 `#e5e7eb`、`#edf0f2`。
- 正文使用 16px 左右，工具栏和标签使用 11-13px。
- 按钮和卡片圆角不超过 8px，贴合项目 UI 约束。
- 不引入 Tailwind、Bootstrap 或其他前端框架。

## Data Flow

页面初始化时：

1. 绑定表单和输入事件。
2. 建立 WebSocket。
3. 加载文档列表并选中第一个文档。
4. 加载当前文档内容和 pending diff。
5. 加载 user profile 列表。

发送消息时：

1. 如果文档有未保存修改，先自动保存。
2. 追加用户消息。
3. 调用 `/api/agent/execute`。
4. 通过 WebSocket 和任务轮询更新消息流。
5. 任务完成后刷新 pending diff。

## Testing

更新 `DemoPageTemplateTest`，用模板断言保护关键结构：

- Figma 顶部栏文案和浅色布局类名存在。
- 主体使用 Flexbox 双栏。
- 左侧包含文档选择、knowledge 上传、编辑器、diff、user profile。
- 右侧包含聊天消息、底部 composer、mode 下拉和发送按钮。
- 原有深色渐变和旧 workbench 标题不再出现。

浏览器级验证以 Spring Boot 模板测试和 `mvn -Dtest=DemoPageTemplateTest test` 为主，必要时运行完整 `mvn test`。

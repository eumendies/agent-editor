# AI Editor Agent API 文档

本文档以当前默认执行链 `agent.v2` 为准。旧 `agent` 包仍保留，但接口默认行为已经切换到 `TaskApplicationService + agent.v2`。

## 基础信息

- 基础地址：`http://localhost:8080`
- Swagger：`/swagger-ui.html`
- WebSocket：`ws://localhost:8080/ws/agent`

## 1. 文档接口

### 1.1 创建文档

`POST /api/v1/documents`

请求参数：

- `title`，query 参数，必填
- `content`，query 参数，必填

响应示例：

```json
{
  "id": "doc-001",
  "title": "My Document",
  "content": "Document content...",
  "originalContent": "Document content...",
  "type": "TEXT"
}
```

### 1.2 获取文档

`GET /api/v1/documents/{id}`

### 1.3 获取全部文档

`GET /api/v1/documents`

### 1.4 更新文档

`PUT /api/v1/documents/{id}`

请求参数：

- `content`，query 参数，必填

### 1.5 删除文档

`DELETE /api/v1/documents/{id}`

## 2. Agent 任务接口

### 2.1 执行任务

`POST /api/v1/agent/execute`

请求体：

```json
{
  "documentId": "doc-001",
  "instruction": "请优化这份文档的结构和表达",
  "mode": "SUPERVISOR",
  "sessionId": "session-123",
  "maxSteps": 10,
  "streaming": true
}
```

字段说明：

- `documentId`：文档 ID，必填
- `instruction`：用户指令，必填
- `mode`：`REACT`、`PLANNING`、`SUPERVISOR`
- `sessionId`：已有 WebSocket 会话 ID，可选
- `maxSteps`：最大步数，可选，默认 10
- `streaming`：保留字段，当前控制器不依赖这个字段控制执行链
- `documentType`：保留字段，当前默认文本文档路径未使用

响应示例：

```json
{
  "taskId": "cbe1fef4-8d7e-4ad1-9be5-5f258a2d2f7f",
  "documentId": "doc-001",
  "status": "COMPLETED",
  "finalResult": "updated document content",
  "startTime": "2026-03-15T14:20:00",
  "endTime": "2026-03-15T14:20:00"
}
```

说明：

- 当前 `/execute` 是同步接口
- 如果请求中带了 `sessionId`，服务端会在任务执行前预绑定 WebSocket 会话，确保 event 不会在 HTTP 返回前丢失

### 2.2 获取任务状态

`GET /api/v1/agent/task/{taskId}`

响应示例：

```json
{
  "taskId": "cbe1fef4-8d7e-4ad1-9be5-5f258a2d2f7f",
  "status": "COMPLETED",
  "finalResult": "updated document content"
}
```

### 2.3 获取任务步骤

`GET /api/v1/agent/task/{taskId}/steps`

说明：

- 返回的仍然是旧 `AgentStep` 结构
- 但当前数据来源已经是 `ExecutionEvent -> LegacyEventAdapter -> AgentStep`
- 它属于兼容展示层，不是新的内部主模型

响应示例：

```json
[
  {
    "taskId": "task-1",
    "stepNumber": 1,
    "type": "THINKING",
    "thought": "iteration 0"
  },
  {
    "taskId": "task-1",
    "stepNumber": 2,
    "type": "ACTION",
    "action": "editDocument"
  },
  {
    "taskId": "task-1",
    "stepNumber": 3,
    "type": "COMPLETED",
    "result": "final output",
    "final": true
  }
]
```

### 2.4 获取支持模式

`GET /api/v1/agent/modes`

响应示例：

```json
["REACT", "PLANNING", "SUPERVISOR"]
```

### 2.5 创建 WebSocket 会话

`POST /api/v1/agent/connect`

响应示例：

```json
{
  "type": "SESSION_CREATED",
  "sessionId": "session-abc123",
  "content": "WebSocket session created. Connect to /ws/agent?sessionId=session-abc123"
}
```

## 3. Trace 接口

### 3.1 获取完整 trace

`GET /api/v1/agent/task/{taskId}/trace`

返回值为 `TraceRecord` 列表，适合开发调试。

每条记录包含：

- `traceId`
- `taskId`
- `timestamp`
- `category`
- `stage`
- `agentType`
- `workerId`
- `iteration`
- `payload`

### 3.2 获取 trace 摘要

`GET /api/v1/agent/task/{taskId}/trace/summary`

响应示例：

```json
{
  "taskId": "task-1",
  "totalRecords": 12,
  "categoryCounts": {
    "MODEL_REQUEST": 2,
    "MODEL_RESPONSE": 2,
    "TOOL_INVOCATION": 1,
    "TOOL_RESULT": 1
  },
  "stages": [
    "react.model.request",
    "react.model.response",
    "runtime.tool.invocation"
  ]
}
```

## 4. Diff 接口

### 4.1 获取文档 diff 历史

`GET /api/v1/diff/document/{documentId}`

### 4.2 比较两段文本

`POST /api/v1/diff/compare`

请求参数：

- `original`，query 参数
- `modified`，query 参数

## 5. WebSocket 消息

连接地址：

- `ws://localhost:8080/ws/agent`

连接成功后，服务端会发送：

```json
{
  "type": "CONNECTED",
  "sessionId": "current-websocket-session-id",
  "content": "Connected to AI Editor Agent"
}
```

客户端可选发送：

```json
{
  "type": "SUBSCRIBE",
  "taskId": "task-1"
}
```

也可以在连接 URL 上直接带 `taskId` 查询参数。

当前常见服务端消息：

- `STEP`
- `COMPLETED`
- `ERROR`
- `PONG`

`STEP` 示例：

```json
{
  "type": "STEP",
  "taskId": "task-1",
  "stepType": "ACTION",
  "content": "editDocument",
  "timestamp": 1742019480000
}
```

`COMPLETED` 示例：

```json
{
  "type": "COMPLETED",
  "taskId": "task-1",
  "content": "final output",
  "timestamp": 1742019485000
}
```

## 6. 模式语义

### REACT

单 agent 直接运行，适合快速编辑和工具驱动场景。

### PLANNING

先规划，再顺序执行每个计划步骤。

### SUPERVISOR

supervisor 分派异构 worker，再统一汇总结果。

## 7. Legacy 说明

旧 `agent` 包仍然存在，但不再是默认执行实现。API 对外行为应以本文档描述的 `agent.v2` 主链为准。

# AI Agent Editor API 文档

本文档只描述当前源码中可以直接验证的 HTTP 与 WebSocket 入口，不再沿用已经过时的旧包层级、旧接口前缀或同步执行描述。

## 基础信息

- Base URL: `http://localhost:8080`
- Swagger UI: `/swagger-ui.html`
- WebSocket: `ws://localhost:8080/ws/agent`

## 1. Document API

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

### 1.2 获取单个文档

`GET /api/v1/documents/{id}`

### 1.3 获取全部文档

`GET /api/v1/documents`

### 1.4 更新文档正文

`PUT /api/v1/documents/{id}`

请求参数：

- `content`，query 参数，必填

### 1.5 删除文档

`DELETE /api/v1/documents/{id}`

## 2. Agent Task API

### 2.1 提交任务

`POST /api/agent/execute`

请求体示例：

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
- `sessionId`：已有 session ID，可选
- `mode`：`REACT`、`PLANNING`、`SUPERVISOR`、`REFLEXION`
- `documentType`：保留字段，当前控制器未依赖这个字段决定执行链
- `maxSteps`：最大步数，可选，默认 `10`
- `streaming`：保留字段，当前控制器不依赖这个字段切换执行方式

响应语义：

- 当前接口为异步提交
- 成功时返回 `202 Accepted`
- 响应中的 `status` 初始通常为 `RUNNING`

响应示例：

```json
{
  "taskId": "cbe1fef4-8d7e-4ad1-9be5-5f258a2d2f7f",
  "documentId": "doc-001",
  "status": "RUNNING",
  "finalResult": null,
  "totalSteps": 0,
  "startTime": "2026-04-11T10:00:00",
  "endTime": null
}
```

补充说明：

- 如果请求携带 `sessionId`，服务端会在任务启动前预绑定该 session，避免首批执行事件丢失
- 异步执行失败时可能返回 `400` 或 `503`

### 2.2 查询任务状态

`GET /api/agent/task/{taskId}`

响应示例：

```json
{
  "taskId": "cbe1fef4-8d7e-4ad1-9be5-5f258a2d2f7f",
  "status": "COMPLETED",
  "finalResult": "updated document content"
}
```

### 2.3 查询任务原生事件

`GET /api/agent/task/{taskId}/events`

返回值为 `ExecutionEvent` 列表，数据来源与 WebSocket 推送共用同一条事件流。

### 2.4 查询 session memory

`GET /api/agent/session/{sessionId}/memory`

返回结构化会话记忆，包括：

- user message
- AI message
- AI tool call
- tool result

### 2.5 查询支持模式

`GET /api/agent/modes`

响应示例：

```json
["REACT", "PLANNING", "SUPERVISOR", "REFLEXION"]
```

## 3. Trace API

### 3.1 获取完整 trace

`GET /api/agent/task/{taskId}/trace`

返回值为 `TraceRecord` 列表。

每条记录包含当前代码里可见的核心字段：

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

`GET /api/agent/task/{taskId}/trace/summary`

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

## 4. Diff API

### 4.1 查询文档 diff 历史

`GET /api/v1/diff/document/{documentId}`

### 4.2 查询待确认改动

`GET /api/v1/diff/document/{documentId}/pending`

如果当前没有 pending change，返回 `404`

### 4.3 应用待确认改动

`POST /api/v1/diff/document/{documentId}/apply`

成功时会：

- 更新正式文档正文
- 写入 diff 历史
- 清除 pending change

### 4.4 丢弃待确认改动

`DELETE /api/v1/diff/document/{documentId}/pending`

### 4.5 比较两段文本

`POST /api/v1/diff/compare`

请求参数：

- `original`，query 参数
- `modified`，query 参数

## 5. Long-Term Memory API

### 5.1 列出用户画像记忆

`GET /api/memory/profiles`

### 5.2 创建用户画像记忆

`POST /api/memory/profiles`

### 5.3 更新用户画像记忆

`PUT /api/memory/profiles/{memoryId}`

### 5.4 删除用户画像记忆

`DELETE /api/memory/profiles/{memoryId}`

## 6. Knowledge Upload API

### 6.1 上传知识文档

`POST /api/v1/knowledge/documents`

表单参数：

- `file`
- `category`

## 7. WebSocket

连接地址：

- `ws://localhost:8080/ws/agent`

当前服务端推送建立在 `ExecutionEvent` 流之上。对外如果只关心 API 调试，优先使用 `/api/agent/task/{taskId}/events`；如果需要实时订阅，再使用 WebSocket。

## 8. 当前接口分层

为避免继续误读，这里明确当前接口分层：

- Agent 任务与 trace：`/api/agent/...`
- 文档与 diff：`/api/v1/documents`、`/api/v1/diff/...`
- 长期记忆：`/api/memory/...`
- 知识库上传：`/api/v1/knowledge/...`

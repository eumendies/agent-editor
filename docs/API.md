# AI Editor Agent API 接口文档

## 概述

AI Editor Agent API 提供基于人工智能的文档编辑功能，支持 ReAct 和 Planning 两种智能体模式。系统通过 WebSocket 实现实时进度推送，并通过差异对比展示文档修改内容。

## 基础信息

- **基础URL**: `http://localhost:8080`
- **API文档**: `http://localhost:8080/swagger-ui.html`
- **WebSocket端点**: `ws://localhost:8080/ws/agent`

---

## 一、文档管理接口

### 1.1 创建文档

**端点**: `POST /api/v1/documents`

**请求参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| title | String | 是 | 文档标题 |
| content | String | 是 | 文档内容 |

**响应示例**:
```json
{
  "id": "doc-001",
  "title": "My Document",
  "content": "Document content...",
  "originalContent": "Document content...",
  "type": "TEXT",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

### 1.2 获取文档

**端点**: `GET /api/v1/documents/{id}`

**路径参数**:
| 参数 | 类型 | 描述 |
|------|------|------|
| id | String | 文档ID |

**响应示例**:
```json
{
  "id": "doc-001",
  "title": "My Document",
  "content": "Document content...",
  "originalContent": "Original content...",
  "type": "TEXT",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T12:00:00"
}
```

### 1.3 获取所有文档

**端点**: `GET /api/v1/documents`

**响应示例**:
```json
[
  {
    "id": "doc-001",
    "title": "Document 1",
    "content": "...",
    ...
  },
  {
    "id": "doc-002", 
    "title": "Document 2",
    "content": "...",
    ...
  }
]
```

### 1.4 更新文档

**端点**: `PUT /api/v1/documents/{id}`

**请求参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| content | String | 是 | 新文档内容 |

### 1.5 删除文档

**端点**: `DELETE /api/v1/documents/{id}`

**响应**: 204 No Content

---

## 二、Agent执行接口

### 2.1 执行Agent任务

**端点**: `POST /api/v1/agent/execute`

**请求体**:
```json
{
  "documentId": "doc-001",
  "instruction": "格式化这段文本，添加适当的段落分隔",
  "mode": "REACT",
  "sessionId": "session-123",
  "maxSteps": 10,
  "streaming": true
}
```

**请求字段说明**:
| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| documentId | String | 是 | 要编辑的文档ID |
| instruction | String | 是 | 用户指令 |
| mode | String | 否 | Agent模式: REACT 或 PLANNING，默认 REACT |
| sessionId | String | 否 | WebSocket会话ID |
| maxSteps | Integer | 否 | 最大执行步数，默认10 |
| streaming | Boolean | 否 | 是否启用流式推送 |

**响应示例**:
```json
{
  "taskId": "task-abc123",
  "documentId": "doc-001",
  "status": "COMPLETED",
  "currentStep": {
    "id": "step-001",
    "taskId": "task-abc123",
    "stepNumber": 5,
    "type": "COMPLETED",
    "thought": "Task completed successfully",
    "result": "Final document content...",
    "timestamp": "2024-01-01T12:00:00",
    "final": true
  },
  "finalResult": "Final document content...",
  "totalSteps": 5,
  "startTime": "2024-01-01T11:55:00",
  "endTime": "2024-01-01T12:00:00"
}
```

### 2.2 获取任务状态

**端点**: `GET /api/v1/agent/task/{taskId}`

**路径参数**:
| 参数 | 类型 | 描述 |
|------|------|------|
| taskId | String | 任务ID |

**响应示例**:
```json
{
  "taskId": "task-abc123",
  "documentId": "doc-001",
  "status": "COMPLETED",
  "currentStep": {...},
  "finalResult": "...",
  "totalSteps": 5,
  "startTime": "2024-01-01T11:55:00",
  "endTime": "2024-01-01T12:00:00"
}
```

### 2.3 获取任务执行步骤

**端点**: `GET /api/v1/agent/task/{taskId}/steps`

**响应示例**:
```json
[
  {
    "id": "step-001",
    "taskId": "task-abc123",
    "stepNumber": 1,
    "type": "THINKING",
    "thought": "Analyzing the instruction...",
    "timestamp": "2024-01-01T11:55:01"
  },
  {
    "id": "step-002", 
    "taskId": "task-abc123",
    "stepNumber": 2,
    "type": "ACTION",
    "action": "edit_document(operation=replace, content=...)",
    "timestamp": "2024-01-01T11:55:02"
  },
  {
    "id": "step-003",
    "taskId": "task-abc123", 
    "stepNumber": 3,
    "type": "OBSERVATION",
    "observation": "Document edited successfully",
    "timestamp": "2024-01-01T11:55:03"
  }
]
```

### 2.4 获取支持的Agent模式

**端点**: `GET /api/v1/agent/modes`

**响应示例**:
```json
["REACT", "PLANNING"]
```

### 2.5 创建WebSocket会话

**端点**: `POST /api/v1/agent/connect`

**响应示例**:
```json
{
  "type": "SESSION_CREATED",
  "sessionId": "session-abc123",
  "content": "WebSocket session created. Connect to /ws/agent?sessionId=session-abc123"
}
```

---

## 三、Diff对比接口

### 3.1 获取文档Diff历史

**端点**: `GET /api/v1/diff/document/{documentId}`

**响应示例**:
```json
[
  {
    "originalContent": "Original text...",
    "modifiedContent": "Modified text...",
    "diffHtml": "<div class='diff-remove'>- Old line</div><div class='diff-add'>+ New line</div>",
    "additions": 5,
    "deletions": 2,
    "timestamp": "2024-01-01T12:00:00"
  }
]
```

### 3.2 对比两个文本

**端点**: `POST /api/v1/diff/compare`

**请求参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| original | String | 是 | 原始内容 |
| modified | String | 是 | 修改后内容 |

---

## 四、WebSocket消息

### 4.1 连接

客户端连接到: `ws://localhost:8080/ws/agent?sessionId=xxx`

### 4.2 消息类型

**服务端推送消息**:

| 类型 | 描述 |
|------|------|
| STEP | Agent执行步骤更新 |
| COMPLETED | 任务完成 |
| ERROR | 执行错误 |
| PROGRESS | 进度更新 |
| CONNECTED | 连接成功 |

**客户端发送消息**:

| 类型 | 描述 |
|------|------|
| PING | 心跳检测 |
| SUBSCRIBE | 订阅任务更新 |

### 4.3 消息示例

**服务端推送步骤**:
```json
{
  "type": "STEP",
  "taskId": "task-abc123",
  "stepType": "THINKING",
  "content": "Analyzing the instruction...",
  "timestamp": 1704067200000
}
```

**客户端订阅**:
```json
{
  "type": "SUBSCRIBE",
  "sessionId": "session-123",
  "taskId": "task-abc123"
}
```

---

## 五、错误响应

### 错误码说明

| 状态码 | 描述 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 错误响应示例

```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Document not found: doc-999"
}
```

---

## 六、Agent模式说明

### 6.1 ReAct模式 (Reasoning + Acting)

ReAct模式结合了推理和行动，通过以下步骤进行文档编辑：

1. **THINKING**: 分析用户指令和当前文档状态
2. **ACTION**: 选择并执行适当的工具
3. **OBSERVATION**: 分析行动结果
4. **REPEAT/COMPLETE**: 继续或完成

### 6.2 Planning模式 (Structured Planning)

Planning模式采用结构化计划方式：

1. **PLANNING**: 根据指令创建详细执行计划
2. **EXECUTION**: 逐步执行计划中的每个步骤
3. **VALIDATION**: 验证每个步骤的结果
4. **COMPLETION**: 所有步骤完成后最终化

---

## 七、枚举值

### AgentStepType
- `THINKING` - 思考中
- `PLANNING` - 计划中
- `ACTION` - 执行动作
- `OBSERVATION` - 观察结果
- `RESULT` - 结果
- `ERROR` - 错误
- `COMPLETED` - 完成

### AgentMode
- `REACT` - ReAct模式
- `PLANNING` - Planning模式

### DocumentType
- `TEXT` - 纯文本
- `MARKDOWN` - Markdown
- `CODE` - 代码
- `JSON` - JSON
- `XML` - XML
- `HTML` - HTML
- `PDF` - PDF
- `DOC` - Word文档

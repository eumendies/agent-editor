# ReAct Agent 智能编辑系统

> 基于 LangChain4j 实现的 ReAct（Reasoning + Acting）模式 AI 文档编辑 Agent

## 📋 项目简介

本项目是一个智能文档编辑系统，通过 ReAct 模式实现多步骤推理和工具调用，能够理解用户自然语言指令并自动执行文档编辑任务。

**核心特性：**
- ✅ **ReAct 模式**：Thought-Action-Observation 循环推理
- ✅ **工具调用**：6 种内置文档编辑工具（基于 `@Tool` 注解）
- ✅ **实时推送**：WebSocket 实时推送执行过程
- ✅ **异步任务**：支持异步执行和任务状态追踪
- ✅ **可扩展架构**：基于抽象类和工厂模式，易于扩展新 Agent 模式

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│  Client (Browser/Postman)                                   │
│  - REST API: 发起任务、查询状态                              │
│  - WebSocket: 接收实时推送                                   │
└───────────────────┬─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│  Controller Layer                                           │
│  - AgentController: 任务管理 API                            │
│  - DocumentController: 文档 CRUD API                        │
│  - DiffController: 差异对比 API                             │
└───────────────────┬─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│  Service Layer                                              │
│  - DocumentService: 业务逻辑、任务管理、差异生成            │
│  - WebSocketService: WebSocket 会话管理、消息推送           │
└───────────────────┬─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│  Agent Layer (核心)                                         │
│  - AgentFactory: Agent 创建工厂                              │
│  - BaseAgent: 抽象基类（执行循环、记忆管理）                │
│  - ReActAgent: ReAct 模式实现                               │
│  - EditorAgentTools: 工具集合（@Tool 注解）                  │
│  - AgentState: 状态管理                                     │
└───────────────────┬─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│  LangChain4j + LLM                                          │
│  - ChatModel: 大模型调用（通义千问）                        │
│  - ToolSpecifications: 工具规范自动生成                    │
│  - ChatMemory: 对话记忆（MessageWindowChatMemory）          │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 目录结构

```
agent-editor/
├── src/main/java/com/agent/editor/
│   ├── AiEditorApplication.java          # Spring Boot 启动类
│   ├── agent/                             # Agent 核心层
│   │   ├── AgentExecutor.java            # Agent 执行器接口
│   │   ├── AgentFactory.java             # Agent 工厂
│   │   ├── AgentState.java               # Agent 状态管理
│   │   ├── BaseAgent.java                # 抽象基类（执行循环）
│   │   ├── ReActAgent.java               # ReAct 模式实现
│   │   └── EditorAgentTools.java         # 工具集合（@Tool 注解）
│   ├── config/                            # 配置类
│   │   ├── LangChainConfig.java          # LangChain4j 配置
│   │   ├── WebSocketConfig.java          # WebSocket 配置
│   │   └── OpenApiConfig.java            # Swagger 配置
│   ├── controller/                        # 控制器层
│   │   ├── AgentController.java          # Agent 任务 API
│   │   ├── DocumentController.java       # 文档 CRUD API
│   │   ├── DiffController.java           # 差异对比 API
│   │   └── PageController.java           # 页面路由
│   ├── dto/                               # 数据传输对象
│   │   ├── AgentTaskRequest.java         # 任务请求
│   │   ├── AgentTaskResponse.java        # 任务响应
│   │   ├── DiffResult.java               # 差异结果
│   │   └── WebSocketMessage.java         # WebSocket 消息
│   ├── model/                             # 数据模型
│   │   ├── AgentMode.java                # Agent 模式枚举
│   │   ├── AgentStep.java                # 执行步骤
│   │   ├── AgentStepType.java            # 步骤类型枚举
│   │   ├── Document.java                 # 文档模型
│   │   └── DocumentType.java             # 文档类型枚举
│   ├── service/                           # 服务层
│   │   └── DocumentService.java          # 文档服务、任务管理
│   └── websocket/                         # WebSocket 层
│       ├── AgentWebSocketHandler.java    # WebSocket 处理器
│       └── WebSocketService.java         # WebSocket 服务
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   ├── static/                           # 静态资源
│   └── templates/                        # 模板文件
└── README.md                             # 项目文档
```

---

## 🔄 核心执行流程

### 1. 任务发起流程

```
用户请求
   │
   ▼
POST /api/v1/agent/execute
{
  "documentId": "doc-001",
  "instruction": "格式化文档并统计字数",
  "mode": "REACT",
  "maxSteps": 10
}
   │
   ▼
AgentController.executeAgentTask()
   │
   ▼
DocumentService.startAgentTaskAsync()
   │
   ├── 创建 AgentState
   ├── 生成 taskId
   ├── 保存到任务缓存
   └── 启动异步线程
       │
       ▼
   AgentFactory.getAgent(AgentMode.REACT)
       │
       ▼
   ReActAgent.builder().build()
       │
       ▼
   AgentExecutor.execute()
       │
       ▼
   BaseAgent.executeLoop() ← 核心循环
```

### 2. ReAct 执行循环（BaseAgent.executeLoop）

```java
while (currentStep < maxSteps && !completed) {
    // 1. 构造消息（从 ChatMemory 获取历史）
    List<ChatMessage> messages = chatMemory.messages();
    
    // 2. 自动生成工具规范
    List<ToolSpecification> toolSpecs = 
        ToolSpecifications.toolSpecificationsFrom(EditorAgentTools.class);
    
    // 3. 调用大模型
    ChatRequest request = ChatRequest.builder()
        .messages(messages)
        .toolSpecifications(toolSpecs)
        .build();
    ChatResponse response = chatLanguageModel.chat(request);
    AiMessage aiMessage = response.aiMessage();
    
    // 4. 添加到记忆
    chatMemory.add(aiMessage);
    
    // 5. 判断是否有工具调用
    if (aiMessage.hasToolExecutionRequests()) {
        for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
            // 6. 执行工具
            String toolResult = tools.executeTool(toolRequest);
            
            // 7. 将结果添加到记忆
            ToolExecutionResultMessage resultMsg = 
                ToolExecutionResultMessage.from(toolRequest, toolResult);
            chatMemory.add(resultMsg);
        }
    }
    
    // 8. 解析响应（ReActAgent 实现）
    AgentStepType stepType = parseResponse(aiMessage, state);
    String content = extractContent(aiMessage, stepType);
    
    // 9. 创建步骤并推送
    AgentStep step = createStep(state, stepType, content, metadata);
    state.addStep(step);
    sendStepUpdate(state, step);  // WebSocket 推送
    
    // 10. 判断是否完成
    if (stepType == COMPLETED) {
        state.setCompleted(true);
        break;
    }
    
    currentStep++;
}
```

### 3. 工具调用机制

```
LangChain4j 自动扫描 @Tool 注解
   │
   ▼
ToolSpecifications.toolSpecificationsFrom(EditorAgentTools.class)
   │
   ├── 扫描所有 @Tool 注解方法
   ├── 提取方法名、描述、参数
   └── 生成 ToolSpecification 列表
       │
       ▼
发送给大模型
   │
   ▼
大模型决定调用工具
   │
   ▼
返回 ToolExecutionRequest
{
  "name": "editDocument",
  "arguments": "{\"content\": \"新内容\"}"
}
   │
   ▼
EditorAgentTools.executeTool(request)
   │
   ├── DefaultToolExecutor 执行
   ├── 根据方法名反射调用对应方法
   └── 返回执行结果
       │
       ▼
ToolExecutionResultMessage 添加到记忆
   │
   ▼
下一轮大模型调用（包含工具执行结果）
```

---

## 🔑 关键类详解

### 1. BaseAgent（抽象基类）

**职责：** 实现 ReAct 执行循环、记忆管理、工具调用

**核心字段：**
```java
protected ChatModel chatLanguageModel;      // 大模型
protected WebSocketService websocketService; // WebSocket 服务
private ChatMemory chatMemory;               // 对话记忆（MessageWindowChatMemory）
private AgentState state;                    // 状态管理
private EditorAgentTools tools;              // 工具集合
```

**核心方法：**

| 方法 | 职责 | 子类实现 |
|------|------|----------|
| `execute()` | 初始化 Agent，启动执行循环 | 最终方法 |
| `executeLoop()` | ReAct 核心循环 | 最终方法 |
| `buildSystemPrompt()` | 构建系统 Prompt | **抽象方法** |
| `createStep()` | 创建执行步骤 | **抽象方法** |
| `parseResponse()` | 解析大模型响应 | **抽象方法** |
| `extractContent()` | 提取响应内容 | **抽象方法** |
| `sendStepUpdate()` | 推送步骤更新 | **抽象方法** |

**记忆管理：**
```java
private void initAgent(...) {
    // 创建消息窗口记忆（保留最近 20 条消息）
    this.chatMemory = MessageWindowChatMemory.builder()
        .maxMessages(20)
        .build();
    
    // 添加系统 Prompt
    String systemPrompt = buildSystemPrompt();
    chatMemory.add(SystemMessage.from(systemPrompt));
    
    // 添加用户消息（文档内容 + 指令）
    String userMessage = buildUserMessage(document, instruction);
    chatMemory.add(UserMessage.from(userMessage));
}
```

---

### 2. ReActAgent（ReAct 模式实现）

**职责：** 实现 ReAct 特有的 Prompt 构建、响应解析逻辑

**核心实现：**

```java
@Override
protected String buildSystemPrompt() {
    return """
        You are an AI-powered document editing agent using the ReAct pattern.
        
        ## ReAct Pattern
        Think step by step:
        1. Analyze the user's instruction
        2. Take ONE action at a time using the available tools
        3. Observe the result
        4. Continue or complete based on the result
        5. Complete the task by calling terminateTask function when finished
        """;
}

@Override
protected AgentStepType parseResponse(AiMessage aiMessage, AgentState state) {
    // 没有工具调用 → 检查是否完成
    if (!aiMessage.hasToolExecutionRequests()) {
        String lower = aiMessage.text().toLowerCase();
        if (lower.contains("done") || lower.contains("task complete")) {
            return AgentStepType.COMPLETED;
        }
        return AgentStepType.THINKING;
    }
    
    // 有工具调用 → 检查是否是 terminateTask
    List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
    for (ToolExecutionRequest request : requests) {
        if ("terminateTask".equals(request.name())) {
            return AgentStepType.COMPLETED;
        }
    }
    return AgentStepType.ACTION;
}
```

---

### 3. EditorAgentTools（工具集合）

**职责：** 定义并实现所有可用的编辑工具

**工具列表：**

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `editDocument` | 编辑文档内容 | content（新内容） |
| `searchContent` | 搜索文本 | pattern（搜索模式） |
| `analyzeDocument` | 分析文档（字数统计） | 无 |
| `terminateTask` | 终止任务 | 无 |
| `respondToUser` | 发送消息给用户 | message（消息内容） |
| `replaceContent` | 替换内容 | pattern, replacement |

**实现方式：**
```java
@Data
@AllArgsConstructor
@Builder
public class EditorAgentTools {
    private AgentState agentState;
    private WebSocketService webSocketService;

    @Tool("Edit the document content with specified changes")
    public String editDocument(String content) {
        Document document = agentState.getDocument();
        document.setContent(content);
        return "Document content edited successfully.";
    }

    @Tool("Search for specific text in the document")
    public String searchContent(String pattern) {
        Document document = agentState.getDocument();
        boolean found = document.getContent().toLowerCase().contains(pattern.toLowerCase());
        return "Search for '" + pattern + "': " + (found ? "Found" : "Not found");
    }

    @Tool("Analyze the document for word count, line count, etc.")
    public String analyzeDocument() {
        String content = agentState.getDocument().getContent();
        int words = content.split("\\s+").length;
        int lines = content.split("\n").length;
        return "Words: " + words + ", Lines: " + lines + ", Chars: " + content.length();
    }

    // ... 其他工具

    public String executeTool(ToolExecutionRequest request) {
        // LangChain4j 默认工具执行器
        return new DefaultToolExecutor(this, request)
            .execute(request, UUID.randomUUID());
    }
}
```

**工具自动注册：**
```java
// LangChain4j 自动扫描 @Tool 注解
List<ToolSpecification> toolSpecs = 
    ToolSpecifications.toolSpecificationsFrom(EditorAgentTools.class);

// 生成结果示例：
[
  {
    "name": "editDocument",
    "description": "Edit the document content with specified changes",
    "parameters": {
      "type": "object",
      "properties": {
        "content": {"type": "string"}
      },
      "required": ["content"]
    }
  },
  // ... 其他工具
]
```

---

### 4. AgentFactory（工厂模式）

**职责：** 创建不同类型的 Agent

```java
@Component
public class AgentFactory {
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private WebSocketService websocketService;

    public AgentExecutor getAgent(AgentMode mode) {
        return ReActAgent.builder()
                .chatLanguageModel(chatModel)
                .websocketService(websocketService)
                .build();
    }

    public AgentExecutor getDefaultAgent() {
        return getAgent(AgentMode.REACT);
    }
}
```

**扩展性：**
```java
// 未来添加 PlanningAgent
public AgentExecutor getAgent(AgentMode mode) {
    switch (mode) {
        case REACT:
            return ReActAgent.builder()...build();
        case PLANNING:
            return PlanningAgent.builder()...build();
        default:
            return getDefaultAgent();
    }
}
```

---

### 5. DocumentService（服务层）

**职责：** 任务管理、文档 CRUD、差异生成

**核心方法：**

```java
@Service
public class DocumentService {
    
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, AgentState> agentTasks = new ConcurrentHashMap<>();
    private final Map<String, List<DiffResult>> diffHistory = new ConcurrentHashMap<>();
    
    @Autowired
    private AgentFactory agentFactory;
    
    // 同步执行
    public AgentTaskResponse executeAgentTask(AgentTaskRequest request) {
        AgentExecutor agent = agentFactory.getAgent(request.getMode());
        AgentState state = agent.execute(document, instruction, sessionId, mode, maxSteps);
        agentTasks.put(state.getTaskId(), state);
        return buildTaskResponse(state);
    }
    
    // 异步执行
    public AgentTaskResponse startAgentTaskAsync(AgentTaskRequest request) {
        // 创建初始状态
        AgentState state = new AgentState(document, instruction, mode);
        agentTasks.put(state.getTaskId(), state);
        
        // 异步执行
        CompletableFuture.runAsync(() -> {
            AgentState resultState = agent.execute(...);
            agentTasks.put(resultState.getTaskId(), resultState);
            // 更新文档、生成差异...
        });
        
        return buildTaskResponse(state);  // 立即返回 taskId
    }
    
    // 查询状态
    public AgentTaskResponse getTaskStatus(String taskId) {
        AgentState state = agentTasks.get(taskId);
        return buildTaskResponse(state);
    }
    
    // 获取执行步骤
    public List<AgentStep> getTaskSteps(String taskId) {
        AgentState state = agentTasks.get(taskId);
        return state.getSteps();
    }
}
```

---

### 6. WebSocketService（实时推送）

**职责：** WebSocket 会话管理、消息推送

```java
@Component
public class WebSocketService {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTasks = new ConcurrentHashMap<>();

    // 注册会话
    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    // 解绑会话
    public void unregisterSession(WebSocketSession session) {
        sessions.remove(session.getId());
        sessionTasks.remove(session.getId());
    }

    // 绑定任务到会话
    public void bindTaskToSession(String sessionId, String taskId) {
        sessionTasks.put(sessionId, taskId);
    }

    // 发送消息到指定会话
    public void sendToSession(String sessionId, WebSocketMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    // 广播消息
    public void broadcast(WebSocketMessage message) {
        String json = objectMapper.writeValueAsString(message);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }
}
```

**消息格式：**
```json
{
  "type": "STEP_UPDATE",
  "taskId": "task-123",
  "stepType": "ACTION",
  "content": "ACTION: editDocument\n",
  "timestamp": 1704067200000
}
```

---

## 🚀 快速开始

### 1. 配置

编辑 `src/main/resources/application.yml`：

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: sk-your-api-key
      model-name: qwen3.5-flash
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      temperature: 0.7
      max-tokens: 4000
```

### 2. 启动

```bash
mvn spring-boot:run
```

### 3. 发起任务

```bash
# 创建 WebSocket 会话
curl -X POST http://localhost:8080/api/v1/agent/connect
# 返回：{"sessionId": "xxx", "type": "SESSION_CREATED"}

# 发起 Agent 任务
curl -X POST http://localhost:8080/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "doc-001",
    "instruction": "格式化文档并统计字数",
    "mode": "REACT",
    "maxSteps": 10,
    "sessionId": "xxx"
  }'
# 返回：{"taskId": "task-123", "status": "RUNNING"}

# 查询任务状态
curl http://localhost:8080/api/v1/agent/task/task-123

# 查询执行步骤
curl http://localhost:8080/api/v1/agent/task/task-123/steps
```

### 4. WebSocket 连接（前端示例）

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/agent?sessionId=xxx');

ws.onopen = () => {
    console.log('Connected');
    ws.send(JSON.stringify({
        type: 'SUBSCRIBE',
        taskId: 'task-123'
    }));
};

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('Received:', message);
    // message.type: STEP_UPDATE
    // message.stepType: THINKING/ACTION/OBSERVATION/COMPLETED
    // message.content: 具体内容
};
```

---

## 📊 执行示例

**用户指令：** "格式化文档并统计字数"

**执行过程：**

```
Step 1:
  Type: ACTION
  Thought: 用户需要格式化文档，我先执行格式化操作
  Action: formatDocument()  [实际工具名：editDocument]
  Result: Document content edited successfully.

Step 2:
  Type: ACTION
  Thought: 格式化已完成，现在需要统计字数
  Action: analyzeDocument()
  Result: Words: 1234, Lines: 56, Chars: 6789

Step 3:
  Type: COMPLETED
  Thought: 任务已完成
  Action: terminateTask()
  Result: done

最终状态：COMPLETED
总耗时：2134ms
总步骤：3
```

---

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | 后端框架 |
| LangChain4j | 0.36+ | 大模型集成 |
| WebSocket | - | 实时推送 |
| Lombok | - | 代码简化 |
| Swagger/OpenAPI | - | API 文档 |
| 通义千问 | Qwen3.5-Flash | 底层大模型 |

---

## 📝 待办事项

- [ ] 添加 Planning Agent 模式
- [ ] 实现记忆机制（短期/长期记忆）
- [ ] 添加错误恢复和重试机制
- [ ] 集成 RAG（检索增强生成）
- [ ] 添加并发控制（信号量限流）
- [ ] 实现任务评估指标

---

## 📄 License

MIT License

---

## 👤 作者

独立开发项目，用于学习和实践 AI Agent 技术

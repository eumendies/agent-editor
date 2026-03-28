# AI Editor Agent

基于 Spring Boot 和 LangChain4j 的智能文档编辑系统。当前默认执行链路基于 `agent.v2`，支持 `REACT`、`PLANNING`、`SUPERVISOR` 三种模式，并提供实时步骤流和高保真 trace 调试能力。

## 当前状态

- 默认执行链：`TaskApplicationService -> agent.v2 task orchestrator -> execution runtime`
- 支持模式：`REACT`、`PLANNING`、`SUPERVISOR`
- 实时观测：WebSocket 兼容事件流
- 调试观测：`ExecutionEvent` + `TraceRecord`
- 演示页面：单页流程演示页，突出三模式对比和 Trace Inspector

旧 `agent` 包仍然保留为 legacy 参考实现，但当前控制器、查询接口、WebSocket 推送和演示路径均默认走 `agent.v2`。

## 核心能力

- 单 agent ReAct 执行：模型决策、工具调用、结果回灌、迭代收口
- 两阶段规划执行：planner 先拆步骤，再由执行 agent 串行落地
- 多 agent 监督编排：supervisor 分派异构 worker，最终统一汇总
- 文档工具体系：工具注册表、白名单裁剪、文档编辑/搜索/分析
- 实时过程展示：步骤流兼容旧前端模型
- 调试链路查看：查看 prompt、tool 调用、tool 结果、编排决策

## 运行架构

```text
Client
  -> REST Controller
  -> TaskApplicationService
  -> TaskOrchestrator
     -> REACT: SingleAgentOrchestrator
     -> PLANNING: PlanningThenExecutionOrchestrator
     -> SUPERVISOR: SupervisorOrchestrator
  -> ExecutionRuntime
  -> ToolRegistry / EventPublisher / TraceCollector
```

关键边界：

- `task` 层负责任务级编排
- `core.runtime` 负责单 agent 决策循环
- `react / planning / supervisor` 放模式专属策略
- `tool / event / trace` 是横切基础设施

## 目录结构

```text
src/main/java/com/agent/editor/
├── agent/
│   ├── ... legacy runtime, 保留参考实现
│   └── v2/
│       ├── core/
│       │   ├── agent/
│       │   ├── runtime/
│       │   └── state/
│       ├── react/
│       ├── planning/
│       ├── supervisor/
│       ├── task/
│       ├── tool/
│       ├── event/
│       └── trace/
├── config/
│   ├── ReactAgentConfig.java
│   ├── PlanningAgentConfig.java
│   ├── SupervisorAgentConfig.java
│   ├── TaskOrchestratorConfig.java
│   ├── ToolConfig.java
│   └── TraceConfig.java
├── controller/
├── service/
├── websocket/
└── dto/
```

## 三种执行模式

### REACT

单 agent 直接运行。`ExecutionRuntime` 驱动 `toolLoopDecision -> tool call -> next toolLoopDecision` 循环，适合直接编辑、小任务和工具驱动场景。

### PLANNING

`PlanningAgentDefinition` 先把用户指令拆成结构化计划，再由 `PlanningThenExecutionOrchestrator` 顺序执行每个步骤。适合多阶段任务。

### SUPERVISOR

`SupervisorOrchestrator` 驱动异构 worker 池。supervisor 只负责分派和收口，具体执行仍复用统一 runtime。适合展示多 agent 编排。

## 快速开始

要求：

- JDK 17
- Maven 3.9+

本项目约定使用下面的 Java 17：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

运行测试：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH \
mvn test
```

启动应用：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH \
mvn spring-boot:run
```

页面入口：

- `/`
- `/editor`

## RAG 检索说明

当前知识库检索默认使用 Milvus 混合检索：

- dense 语义检索基于 `embedding`
- lexical 检索只覆盖 `heading + chunkText`
- 默认集合名为 `knowledge_chunks_v2`

如果你是从旧的向量检索版本升级过来，需要重新上传知识库文档，才能重建混合检索所需的全文字段和索引。旧集合中的历史数据不会自动迁移到 `knowledge_chunks_v2`。

## 对外接口

常用接口：

- `POST /api/v1/agent/execute`
- `GET /api/v1/agent/task/{taskId}`
- `GET /api/v1/agent/task/{taskId}/steps`
- `GET /api/v1/agent/task/{taskId}/trace`
- `GET /api/v1/agent/task/{taskId}/trace/summary`
- `GET /api/v1/documents`
- `GET /api/v1/diff/document/{documentId}`

更详细说明见 [docs/API.md](/Users/eumendies/code/java/learn/agent-editor/docs/API.md)。

## 可观测性

当前有两条观测通道：

- `ExecutionEvent`
  面向页面步骤流、任务查询、WebSocket 推送
- `TraceRecord`
  面向开发调试，保留完整 prompt、模型响应、工具调用、编排决策

`Trace Inspector` 已接入 demo 页面，可直接查看任务的 trace 详情。

## Legacy 说明

`src/main/java/com/agent/editor/agent` 下的旧运行时仍然保留，用于参考和回退研究：

- `BaseAgent`
- `ReActAgent`
- `AgentFactory`
- `EditorAgentTools`

但它们不再是默认执行链，不应作为后续新功能开发的主扩展点。

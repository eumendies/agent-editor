# AI Agent Editor

一个基于 Spring Boot、LangChain4j 和 Milvus 的 AI 文档编辑与多 Agent 编排实验项目，重点放在 agent 模式、工具调用、记忆机制和文档改写执行链。

## Why This Project

这个仓库的重点不是做一个通用 AI 应用脚手架，而是围绕“文档编辑”这个具体场景，持续实验不同的 agent 编排方式：

- 单 agent 的直接工具循环
- 先规划再执行的阶段式编辑
- supervisor 驱动的多 worker 协作
- actor / critic 反思式修订

如果你想看的是一个 agent 如何接入文档工具、如何携带会话记忆、如何结合长期记忆和知识检索，以及如何把结果变成待确认的文档改动，这个仓库的主线就是这些问题。

## Highlights

- 支持 4 种执行模式：`REACT`、`PLANNING`、`SUPERVISOR`、`REFLEXION`
- agent 任务通过 `/api/agent/execute` 异步提交，并可查询任务状态与事件流
- 文档编辑不直接覆盖正文，而是先进入 pending change 确认流程
- 工具层按执行角色做权限控制，区分写作、评审、研究和记忆职责
- 具备 session memory、long-term memory、knowledge retrieval 三层辅助能力
- 支持基于 Milvus 的知识检索，用于文档编辑相关的上下文补充

## Agent Modes

### REACT

`REACT` 是最直接的单 agent 模式。`ReActAgentOrchestrator` 会准备好初始上下文和可用工具，然后把任务交给统一的工具循环 runtime 执行，直到得到最终内容。

适合单轮可收敛的任务，例如局部改写、润色、结构微调、针对当前文档的直接编辑。

### PLANNING

`PLANNING` 采用两阶段执行。`PlanningThenExecutionOrchestrator` 先让 planner 生成结构化计划，再把每个计划步骤交给执行 agent 逐步落地；每一步生成的内容都会成为下一步输入。

适合目标更复杂、需要阶段拆分的任务，例如多段式改写、先梳理再重写、按步骤完成大纲调整。

### SUPERVISOR

`SUPERVISOR` 由 `SupervisorOrchestrator` 驱动。每一轮会根据当前文档内容、已有 worker 结果和会话状态构建 `SupervisorContext`，再由 supervisor 决定继续分派 worker，还是收口完成任务。

当前代码里，supervisor 机制围绕多 worker 协作展开，适合职责拆分明确的任务，例如研究、写作、评审、记忆整理需要分步协同的场景。

### REFLEXION

`REFLEXION` 是 actor / critic 循环。`ReflexionOrchestrator` 会先让 actor 生成结果，再让 critic 审查当前版本；如果 verdict 不是 `PASS`，critic 的反馈会回灌给 actor，进入下一轮修订。

适合更强调自我审查和多轮修正质量的任务，例如需要反复校验表达、结构或一致性的文档编辑。

## Execution Flow

当前任务主链可以概括为：

```text
AgentController
  -> TaskApplicationService
  -> TaskOrchestrator
     -> ReActAgentOrchestrator
     -> PlanningThenExecutionOrchestrator
     -> SupervisorOrchestrator
     -> ReflexionOrchestrator
  -> runtime / tools / memory
  -> pending document change
```

几个关键事实：

- `POST /api/agent/execute` 走的是异步提交模型，控制器返回 `202 Accepted`
- `TaskApplicationService` 负责校验文档、创建任务、映射模式、后台提交执行
- 任务状态通过 `GET /api/agent/task/{taskId}` 查询
- 原生执行事件可通过 `GET /api/agent/task/{taskId}/events` 查看
- agent 产出的新内容不会立刻写回原文，而是先进入 pending change，等待应用或丢弃

对应的 pending change 接口位于：

- `GET /api/v1/diff/document/{documentId}/pending`
- `POST /api/v1/diff/document/{documentId}/apply`
- `DELETE /api/v1/diff/document/{documentId}/pending`

## Code Map

如果你是第一次进入源码，建议先从这些包看起：

- `src/main/java/com/agent/editor/agent/core`
  通用 agent 抽象、运行时、上下文、状态和内存模型
- `src/main/java/com/agent/editor/agent/task`
  任务编排入口、模式路由、任务请求与任务结果
- `src/main/java/com/agent/editor/agent/react`
  单 agent ReAct 执行链
- `src/main/java/com/agent/editor/agent/planning`
  planner + execution 两阶段模式
- `src/main/java/com/agent/editor/agent/supervisor`
  supervisor 决策和 worker 协作
- `src/main/java/com/agent/editor/agent/reflexion`
  actor / critic 反思循环
- `src/main/java/com/agent/editor/agent/tool`
  文档工具、记忆工具、工具权限控制
- `src/main/java/com/agent/editor/agent/memory`
  session memory 存储与压缩相关实现
- `src/main/java/com/agent/editor/service`
  应用层服务，连接 controller、文档服务和 agent 编排
- `src/main/java/com/agent/editor/controller`
  HTTP 入口、任务查询、文档 CRUD、diff、记忆与知识库上传接口

## Memory and Retrieval

### Session Memory

会话记忆由 `SessionMemoryStore` 承载，任务执行中的用户消息、AI 消息、tool call 和 tool result 会进入会话转录。当前可通过：

- `GET /api/agent/session/{sessionId}/memory`

查看某个 session 的结构化记忆内容。

### Long-Term Memory

长期记忆主要用于保存更稳定的用户画像和文档决策相关信息。应用层通过 `LongTermMemoryRetrievalService` 和对应写入服务把这些记忆提供给 agent 使用。当前管理入口是：

- `GET /api/memory/profiles`
- `POST /api/memory/profiles`
- `PUT /api/memory/profiles/{memoryId}`
- `DELETE /api/memory/profiles/{memoryId}`

### Knowledge Retrieval

知识检索通过 `KnowledgeRetrievalService` 完成：先做 embedding，再调用 repository 执行 hybrid search。知识库上传入口目前是：

- `POST /api/v1/knowledge/documents`

默认配置下，Milvus 集合名为 `knowledge_chunks_v2`。

## Quick Start

### Requirements

- JDK 17
- Maven 3.9+
- Docker 与 Docker Compose
- 本地可用的 Milvus 实例

请先确认本机 `java -version` 为 17，且 `mvn -version` 使用的也是同一套 JDK。

```bash
java -version
mvn -version
```

如果你的默认 JDK 不是 17，请自行将 `JAVA_HOME` 指向本机的 JDK 17 安装目录后再执行 Maven 命令。

### Install Milvus

当前项目默认连接本地 `localhost:19530` 的 Milvus 实例。推荐按官方文档使用 Docker Compose 安装 standalone 版本：

- 官方文档：<https://milvus.io/docs/zh/install_standalone-docker-compose.md>

最小启动步骤如下：

```bash
wget https://github.com/milvus-io/milvus/releases/download/v2.6.13/milvus-standalone-docker-compose.yml -O docker-compose.yml
docker compose up -d
```

启动后，默认端口与当前项目配置对应关系如下：

- Milvus: `localhost:19530`
- Milvus WebUI: `http://127.0.0.1:9091/webui/`

停止并清理：

```bash
docker compose down
rm -rf volumes
```

### Configure Environment

先复制 `.env.example`：

```bash
cp .env.example .env
```

然后填写至少这些模型配置：

```dotenv
CHAT_API_KEY=
CHAT_MODEL_NAME=
CHAT_BASE_URL=
EMBEDDING_API_KEY=
EMBEDDING_MODEL_NAME=
EMBEDDING_BASE_URL=
```

### OpenDataLoader PDF Dependency Notes

项目依赖 `org.opendataloader:opendataloader-pdf-core`。如果你在本地拉取依赖失败，先检查 Maven 镜像配置。

当前 `pom.xml` 中声明了 `vera-dev` 仓库。如果你的 Maven 镜像策略会错误代理或覆盖这个仓库，`opendataloader-pdf-core` 可能无法正常解析。遇到这种情况时：

- 检查 `settings.xml` 里的 `mirrors` 配置
- 确认你的镜像规则没有把 `vera-dev` 也错误纳入代理
- 如果你使用阿里云等统一镜像，确保镜像排除了 `vera-dev`

可以用下面的方式理解这个要求：

```xml
<mirror>
  <id>your-mirror</id>
  <mirrorOf>*,!vera-dev</mirrorOf>
</mirror>
```

### Run

运行测试：

```bash
mvn test
```

启动应用：

```bash
mvn spring-boot:run
```

默认端口为 `8080`。

## Entry Points

页面入口：

- `/`
- `/editor`

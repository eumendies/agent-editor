# README Refresh Design

## Goal

将仓库根目录的 `README.md` 重写为更接近 GitHub 开源仓库常见写法的首页文档，同时确保所有关键信息都以当前源码为准，不再沿用已经过时的包结构、接口路径或能力描述。

## Audience

- 首次访问仓库、希望快速理解项目定位的读者
- 需要从源码主线切入的协作者
- 需要启动项目并体验 agent 能力的开发者

本次采用 `中文优先`、`展示与开发接入兼顾`、`文本为主` 的写法。

## Core Direction

README 首页采用 `agent-first` 结构，优先解释当前仓库最有价值的部分：

- agent 模式与执行差异
- 实际任务执行链
- 当前代码结构中的 agent 主干模块
- 记忆与检索如何服务 agent

WebSocket、Swagger 之类配套能力只保留简短索引，不占据首页叙事中心。

## Source of Truth

README 改写必须以当前源码为事实来源，尤其以下约束必须严格遵守：

- 不再使用 `agent.v2` 之类已经删除的包名
- 不再把旧 `agent` 说成 legacy/v1 参考实现
- 不再使用与当前控制器不一致的 `/api/v1/agent/...` 路径
- 不再写代码里无法直接证明的 UI/trace 集成表述
- 模式说明必须覆盖当前真实支持的四种模式：`REACT`、`PLANNING`、`SUPERVISOR`、`REFLEXION`

## Planned README Structure

### 1. Title and One-Line Summary

开头直接说明项目是一个基于 Spring Boot、LangChain4j、Milvus 的 AI 文档编辑与多 Agent 编排实验项目。

### 2. Why This Project

用一小段说明仓库重点不是通用脚手架，而是围绕文档编辑场景探索不同 agent 编排方式、工具权限、记忆机制与任务执行链。

### 3. Highlights

只列当前代码可证明的能力摘要，避免营销化语言：

- 异步 agent 任务提交与状态查询
- 四种 agent 模式
- 文档编辑工具与权限控制
- 会话记忆、长期记忆、知识检索
- 候选改动确认流程

### 4. Agent Modes

这是 README 主体。逐一介绍：

- `REACT`
- `PLANNING`
- `SUPERVISOR`
- `REFLEXION`

每种模式都回答三件事：

- 解决什么问题
- 当前 orchestrator 如何执行
- 典型适用任务

### 5. Execution Flow

用精简流程说明真实主链：

`AgentController -> TaskApplicationService -> TaskOrchestrator -> specific orchestrator/runtime -> tools/memory -> pending document change`

同时点明：

- `/api/agent/execute` 为异步提交
- 任务状态通过 `/api/agent/task/{taskId}` 查询
- agent 结果先进入 pending change，而不是直接覆盖文档

### 6. Code Map

围绕 agent 主干模块解释代码地图：

- `agent/core`
- `agent/task`
- `agent/react`
- `agent/planning`
- `agent/supervisor`
- `agent/reflexion`
- `agent/tool`
- `agent/memory`
- `service`
- `controller`

### 7. Memory and Retrieval

聚焦与 agent 行为直接相关的三层能力：

- session memory
- long-term memory
- knowledge retrieval

### 8. Quick Start

只保留必要开发接入内容：

- JDK 17 / Maven 3.9+
- `.env.example`
- `mvn test`
- `mvn spring-boot:run`

### 9. Entry Points

以索引方式列出页面和 API 入口，详细说明继续交给 `docs/API.md`：

- `/`
- `/editor`
- `/api/agent/execute`
- `/api/agent/task/{taskId}`
- `/api/agent/task/{taskId}/events`
- `/api/agent/session/{sessionId}/memory`
- `/api/memory/profiles`

## Writing Rules

- 语言风格保持工程化、克制，不写夸张宣传词
- 章节标题接近开源仓库常见写法，但正文仍以中文表达
- 只保留能帮助读者理解主线的信息，删掉冗余历史说明
- 命令、路径、接口前缀、模式名全部与当前代码对齐

## Non-Goals

- 不补写截图、徽章、演示 GIF
- 不顺带重写 `docs/API.md`
- 不为 README 引入未经核实的新概念或未来路线图
- 不把首页变成全量接口参考手册

## Verification

完成 README 改写后，需要逐项核对以下事实：

1. 模式数量与 `AgentMode` 枚举一致
2. 任务入口与 `AgentController` 路径一致
3. 页面入口与 `PageController` 一致
4. 运行命令与 `pom.xml` / `application.yml` / `.env.example` 一致
5. 关键架构描述与 `TaskApplicationService`、`TaskOrchestratorConfig`、各 orchestrator 实现一致

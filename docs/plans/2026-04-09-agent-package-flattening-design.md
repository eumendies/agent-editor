# Agent Package Flattening Design

## Goal

彻底废弃 `agent.v1` 与所有 “v2 只是过渡版本” 的命名，把当前运行中的 agent runtime 收敛为唯一正式实现：

- `com.agent.editor.agent.v2.*` 平移为 `com.agent.editor.agent.*`
- 删除 `com.agent.editor.agent.v1.*`
- 删除 `Legacy*` / `*V2*` 命名和对应兼容入口
- 删除 `/api/v2/*` 风格路径，统一到最终 API 路径，不保留兼容别名

## Scope

### Source Packages

- move `src/main/java/com/agent/editor/agent/v2/*` -> `src/main/java/com/agent/editor/agent/*`
- delete `src/main/java/com/agent/editor/agent/v1`
- update all `package` / `import` references from `com.agent.editor.agent.v2...` to `com.agent.editor.agent...`

### Test Packages

- move `src/test/java/com/agent/editor/agent/v2/*` -> `src/test/java/com/agent/editor/agent/*`
- update all test imports and package declarations accordingly
- remove tests that only validate v1 / legacy compatibility

### Naming Cleanup

- remove `LegacyAgentService`; keep `TaskApplicationService` as the single application entry for agent execution
- rename `AgentV2Controller`, `AgentV2WebSocketHandler`, `AgentV2ConfigurationSplitTest`, `WebSocketServiceV2Test`, and similar classes to final names without `V2`
- clean comments, docs, and tests that still describe the runtime as “v2” unless the text is historical documentation that will be deleted

### API Cleanup

- remove split versioned agent endpoints
- converge to final paths such as:
  - `/api/agent`
  - `/api/memory`
  - `/api/agent/task/{taskId}/trace`
- update frontend/backend tests to stop calling `/api/v2/*`
- do not keep compatibility aliases or forwarding controllers

## Architecture

本次变更是一次性结构收口，不引入新的兼容层。运行时实现保持现有 agent runtime 行为，只调整包路径、命名和对外入口，使代码结构与实际运行架构一致。内部执行建议先完成物理目录移动，再统一修复 `package` / `import`，最后收口 controller、websocket、tests 和前端调用，避免在中间态长期保留双语义命名。

## Data Flow Impact

- task execution still enters through `TaskApplicationService`
- orchestrators, runtimes, tools, memory, trace, and websocket events keep existing behavior
- `USER_PROFILE` / long-term memory flow does not change in behavior, only package references and API paths change

## Error Handling

- startup must fail fast if any Spring bean name, package scan, or import remains stale after the move
- no fallback routing or deprecated aliasing will be added
- compile/test failures are treated as migration gaps rather than compatibility work

## Testing

- full compile + targeted test pass for config, controller, websocket, orchestrator, tool, memory, and trace paths
- global search must confirm there are no remaining references to:
  - `com.agent.editor.agent.v1`
  - `com.agent.editor.agent.v2`
  - `LegacyAgentService`
  - `AgentV2`
- controller tests must be updated to new final API paths only

## Non-Goals

- no behavioral redesign of the agent runtime
- no temporary compatibility adapters
- no API version coexistence

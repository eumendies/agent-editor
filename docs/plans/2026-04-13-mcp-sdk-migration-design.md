# MCP SDK Migration Design

## Goal

Replace the handwritten MCP protocol transport and JSON-RPC DTO layer with the official LangChain4j MCP SDK while preserving the project's local tool-governance model, including local tool naming, explicit allow-list registration, permission control, and result formatting.

## Scope

In scope:

- add `langchain4j-mcp` as a project dependency
- replace the handwritten `StreamableHttpMcpClient` transport logic with LangChain4j MCP SDK client primitives
- keep local Spring MCP configuration binding under `agent.mcp`
- keep explicit local-to-remote tool mappings
- keep `ToolRegistry` as the runtime registration boundary
- keep `ExecutionToolAccessPolicy` as the permission boundary
- adapt SDK `ToolSpecification` and `ToolExecutionResult` into the existing local `ToolHandler` contract
- migrate MCP-focused tests from handwritten protocol assertions to SDK bridge assertions

Out of scope:

- replacing the project's `ToolRegistry` runtime with LangChain4j `ToolProvider`
- changing prompt strategy beyond what has already been added for `webSearch`
- exposing additional MCP resource or prompt capabilities
- implementing generic full auto-discovery of all remote MCP tools

## Current Problem

The current branch successfully wires MCP-backed tools into the runtime, but it does so by handcrafting a protocol layer:

- custom JSON-RPC request/response DTOs
- handwritten `initialize`, `tools/list`, and `tools/call` request assembly
- handwritten response parsing and session-header handling

That design is sufficient for the current tests, but it cannot provide strong confidence that the implementation matches the full MCP SDK behavior supported by LangChain4j and remote MCP servers.

The review concern is technically valid: protocol correctness should be delegated to the MCP SDK where possible, not duplicated in local handwritten transport code.

## Recommended Approach

Use LangChain4j MCP SDK transport and client primitives for all protocol and transport behavior, but retain the project's local bridge layer for governance and runtime integration.

Specifically:

- create SDK transport via `StreamableHttpMcpTransport`
- create SDK client via `DefaultMcpClient`
- wrap it in a thin local adapter that exposes only the bridge-facing methods this project needs
- keep local mapping and permission logic unchanged at the architectural boundary

Why this approach:

- protocol details move to the SDK
- current project runtime remains intact
- local `webSearch` naming and permission behavior remain stable
- migration is contained to the `agent.mcp` integration layer rather than the whole agent system

## Alternatives Considered

### 1. Keep handwritten transport and just increase tests

Pros:

- smallest code churn
- no new dependency line

Cons:

- still leaves protocol behavior owned by this repository
- still requires ongoing maintenance for MCP protocol details
- does not address the core review concern

### 2. Use SDK transport/client and keep local governance bridge

Pros:

- best balance of correctness and containment
- preserves current runtime architecture
- isolates the migration to the MCP integration layer

Cons:

- introduces a beta SDK dependency
- requires rewriting MCP-specific tests

### 3. Adopt SDK `McpToolProvider` directly in the runtime

Pros:

- most “native” SDK usage
- less local bridge code over time

Cons:

- cuts across the current `ToolRegistry -> ToolHandler` runtime design
- much larger refactor than required for this review item
- mixes protocol replacement with runtime architecture change

## Architecture

The architecture after migration should look like this:

- `agent.mcp.config`
  - keep `McpProperties`
  - keep `McpServerProperties`
  - keep `McpToolProperties`
- `agent.mcp.client`
  - remove handwritten JSON-RPC DTOs
  - replace handwritten transport client with a thin SDK adapter
- `agent.mcp.tool`
  - keep `McpBackedToolHandler`
  - keep `McpToolResultFormatter`
- `config`
  - keep `McpConfig`, but change client creation to SDK-backed construction

The key design rule remains:

本地 runtime 仍然只认识 `ToolHandler`，不直接暴露 SDK 的 MCP client 或 provider 作为运行时边界。

This means the SDK owns protocol correctness, while the application still owns:

- which remote tools become locally visible
- what their local names are
- which execution roles can access them
- how their results are normalized into local runtime messages

## Code Replacement Plan

### Remove or retire

These handwritten protocol classes should be removed or fully replaced:

- `agent.mcp.client.McpJsonRpcRequest`
- `agent.mcp.client.McpJsonRpcResponse`
- `agent.mcp.client.McpInitializeResult`
- handwritten `agent.mcp.client.StreamableHttpMcpClient`

### Add or replace

Add a thin SDK-facing adapter, for example:

- `agent.mcp.client.SdkMcpClientAdapter`

This adapter should internally hold:

- `StreamableHttpMcpTransport`
- `DefaultMcpClient`

It should expose only the bridge-level methods needed by this application, such as:

- list tool metadata
- execute a tool call

The adapter should not re-implement protocol behavior. It should only translate between SDK objects and the application's local MCP bridge objects.

## SDK Client Construction

`McpConfig` should create clients like this:

1. validate active server configuration
2. build `StreamableHttpMcpTransport`
   - `url(serverProperties.getBaseUrl())`
   - `customHeaders(serverProperties.getHeaders())`
   - optional timeout/logging settings if needed
3. build `DefaultMcpClient`
   - `transport(...)`
   - `key(serverKey)`
   - `clientName("ai-editor")`
   - `clientVersion("1.0.0")`
   - `protocolVersion(...)`
   - `cacheToolList(true)`
4. wrap it in `SdkMcpClientAdapter`

This keeps the startup validation flow from the current `McpConfig`, but hands the protocol work to the SDK.

## Tool Discovery And Registration

`McpConfig` should continue using explicit local mappings:

- read active configured servers
- fetch remote tool list from the SDK client
- verify that each configured `remote-tool-name` exists
- create one local `McpBackedToolHandler` per explicit mapping
- register those handlers into `ToolRegistry`

No full auto-registration should be introduced during this migration.

This migration is about replacing protocol ownership, not about expanding capability scope.

## ToolSpecification Bridge

The SDK already returns `ToolSpecification` from `DefaultMcpClient.listTools()`.

The bridge should:

- use the SDK-provided remote `ToolSpecification` as the schema source of truth
- override the tool name with configured local `tool-name`
- override description when local config explicitly provides one

This keeps the schema generated by the SDK while preserving stable local naming and local prompt references.

Compared with the current branch, the schema translation code in `McpBackedToolHandler` should shrink significantly or disappear entirely.

## Tool Execution Bridge

`McpBackedToolHandler.execute(...)` should:

1. create SDK `ToolExecutionRequest`
   - name = configured `remote-tool-name`
   - arguments = existing local invocation JSON
2. call SDK `DefaultMcpClient.executeTool(...)`
3. read SDK `ToolExecutionResult`
4. map it into local `ToolResult`

Mapping rules:

- `isError()` does not become an exception
- `resultText()` should be preserved
- `result()` should be serialized when it contains structured result data
- `updatedContent` stays `null`

True transport/client failures from the SDK should be converted into `RecoverableToolException`.

工具级错误继续走“模型可见结果”，基础设施错误继续走“runtime 可恢复异常”这条边界，不要因为改成 SDK 就把这两个语义混在一起。

## Result Formatting

`McpToolResultFormatter` should be retained, but its input should shift from the current local handwritten call-result DTO to a normalized adapter output derived from SDK `ToolExecutionResult`.

Recommended adapter output contract:

- `boolean error`
- `Object structuredContent`
- `String text`

The formatter can keep its current behavior:

- if there is structured content, return JSON containing `structuredContent` and optional `text`
- otherwise return plain text

This preserves current runtime behavior and existing tests with only small updates.

## Permissions And Prompting

No architectural change is needed in:

- `ExternalToolAccessPolicy`
- `ExecutionToolAccessPolicy`
- prompt text for researcher/writer

Those changes belong to the local governance layer and remain correct after the transport migration.

## Testing Strategy

### Replace handwritten transport tests

Current handwritten protocol tests should be rewritten:

- old focus: request body shape, session-header reuse, error JSON parsing
- new focus: SDK adapter behavior and error translation

New MCP adapter tests should prove:

- SDK-backed adapter lists tools and exposes expected local descriptor data
- SDK-backed adapter executes remote tools and returns normalized results
- SDK client exceptions become `RecoverableToolException`

### Keep bridge and policy tests

Keep these tests and update only where necessary:

- `McpBackedToolHandlerTest`
- `McpConfigTest`
- `ExecutionToolAccessPolicyTest`
- `ExternalToolAccessPolicyTest`
- `ResearcherAgentContextFactoryTest`
- `GroundedWriterAgentContextFactoryTest`
- `McpBackedToolRuntimeTest`

These tests still validate the important local behavior after protocol ownership moves to the SDK.

### Full verification

After migration:

- run focused MCP bridge tests
- run full `mvn test`

This is especially important because the SDK dependency is on a beta line.

## Risks And Mitigations

- Risk: `langchain4j-mcp 1.11.0-beta19` may have compatibility issues with `langchain4j 1.11.0`.
  Mitigation: keep the migration scoped, run full suite, avoid expanding MCP feature scope in the same change.

- Risk: SDK `ToolExecutionResult.result()` may use shapes not directly serializable by current formatter assumptions.
  Mitigation: normalize in the SDK adapter and add tests for text-only, structured-only, and mixed outputs.

- Risk: local naming override may get lost if SDK `ToolSpecification` is used directly.
  Mitigation: keep local tool-name override in the bridge layer.

- Risk: migration could accidentally re-open tool visibility to unintended roles.
  Mitigation: leave permission policy classes unchanged and keep existing tests.

## Success Criteria

- handwritten MCP protocol DTOs and transport logic are removed from the branch
- MCP transport and protocol behavior are owned by LangChain4j MCP SDK
- local `webSearch` tool registration still works through `ToolRegistry`
- local tool naming and `ExecutionToolAccessPolicy` behavior remain unchanged
- MCP runtime integration tests still pass
- full project test suite passes after the SDK migration

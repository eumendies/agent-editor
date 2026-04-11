# MCP Web Search Design

## Goal

Add real-time web search capability to the agent runtime by introducing a lightweight, reusable MCP integration layer and registering Aliyun Bailian WebSearch as the first MCP-backed tool.

## Scope

In scope:

- add a generic MCP server configuration model under `agent.mcp`
- support `streamableHttp` MCP servers
- initialize MCP sessions and call remote tools over HTTP
- register MCP-backed tools into the existing `ToolRegistry`
- expose a new local tool name `webSearch`
- control `webSearch` visibility through `ExecutionToolAccessPolicy`
- allow `webSearch` for researcher and main execution roles
- return MCP tool results through the existing `ToolResult` and runtime memory flow
- add unit and integration-style tests for configuration, client behavior, registration, permissions, and handler execution

Out of scope:

- implementing full dynamic MCP tool discovery for every remote tool without local allow-listing
- exposing MCP tools to review or memory roles by default
- adding UI-specific MCP capabilities
- redesigning the existing agent runtime or tool loop protocol
- changing document-tool ownership boundaries inside `DocumentToolAccessPolicy`

## Current Problem

The project already has a stable local tool runtime built around `ToolHandler`, `ToolRegistry`, `ToolSpecification`, and `ExecutionToolAccessPolicy`, but it has no MCP client layer.

That means:

- the agent cannot use external real-time search tools
- MCP server definitions such as Aliyun Bailian WebSearch cannot be consumed directly
- adding one-off HTTP tools would solve the immediate need but would duplicate integration logic when more MCP services are added later

## Recommended Approach

Introduce a lightweight MCP integration layer that converts configured remote MCP tools into locally registered `ToolHandler` instances, then expose Aliyun WebSearch as the first MCP-backed tool using the local tool name `webSearch`.

Why this approach:

- it matches the current architecture instead of replacing it
- it keeps MCP as an infrastructure concern and keeps the rest of the runtime unchanged
- it allows future MCP servers to be added by configuration plus small explicit mappings
- it preserves existing tool allow-list enforcement through `ExecutionToolAccessPolicy`

## Alternatives Considered

### 1. Hardcode Aliyun WebSearch as a special HTTP tool

Add a dedicated local `webSearch` implementation that directly calls the Aliyun endpoint and ignores general MCP structure.

Pros:

- fastest path to one working tool
- smallest initial code diff

Cons:

- duplicates transport and session logic
- does not scale to future MCP services
- drifts from the provided MCP configuration model

### 2. Lightweight MCP integration plus explicit local tool mappings

Add reusable MCP transport and registration support, but only expose locally approved tools via explicit mappings.

Pros:

- reusable without overbuilding
- safer than full dynamic discovery
- fits current registry and permission model

Cons:

- more code than a one-off integration
- requires configuration, transport, and test coverage together

### 3. Full dynamic MCP discovery and automatic registration

Initialize the server, fetch all remote tools, and auto-register everything exposed by the MCP server.

Pros:

- most generic
- minimal per-tool mapping work after the framework exists

Cons:

- too much surface area for a first integration
- harder to enforce stable local naming and permissions
- raises prompt, safety, and compatibility complexity immediately

## Architecture

The MCP integration should be added as a thin adapter layer beside the current local tools:

- `agent.mcp.config`
  - `McpProperties`
  - `McpServerProperties`
  - `McpToolProperties`
- `agent.mcp.client`
  - `McpClient`
  - `StreamableHttpMcpClient`
  - request/response DTOs needed for initialize, tools/list, and tools/call
- `agent.mcp.tool`
  - `McpBackedToolHandler`
  - `McpToolResultFormatter`
- `config`
  - `McpConfig`

`McpConfig` should create MCP clients from configuration and register one local `ToolHandler` per configured tool mapping into the existing `ToolRegistry`.

这样做的关键约束是“MCP 是远端能力来源，但本地 runtime 仍然只认识 `ToolHandler`”。这样可以避免把现有 tool loop、memory、event、allowed-tools 机制全部改成另一套协议。

## Configuration Model

Add Spring configuration under `agent.mcp`.

Recommended YAML shape:

```yaml
agent:
  mcp:
    servers:
      web-search:
        type: streamableHttp
        description: Aliyun Bailian WebSearch MCP server
        active: true
        name: AliyunBailianMCP_WebSearch
        base-url: https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp
        headers:
          Authorization: Bearer ${DASHSCOPE_API_KEY:}
        tools:
          - tool-name: webSearch
            remote-tool-name: webSearch
            description: Search real-time information from the public web
```

Design rules:

- only `active=true` servers are initialized
- first version supports only `streamableHttp`
- headers are copied as configured after Spring placeholder expansion
- tools must be explicitly mapped; no implicit “register everything”
- local `tool-name` is the canonical name seen by the model and permission system

## MCP Protocol Flow

For each active server:

1. create `StreamableHttpMcpClient`
2. send `initialize` before any other protocol call
3. cache `Mcp-Session-Id` if the server returns it
4. call `tools/list`
5. verify that each configured `remote-tool-name` exists
6. build `McpBackedToolHandler` for each configured mapping

At runtime, `McpBackedToolHandler.execute(...)` should:

1. read the incoming local tool arguments JSON string
2. call MCP `tools/call` for the mapped remote tool
3. convert the MCP response into a stable `ToolResult`

The client should preserve per-server session state so repeated tool calls reuse the same MCP session when the remote server requires it.

## Tool Specification Strategy

`McpBackedToolHandler` must expose a local `ToolSpecification`.

First version should derive the local specification from the remote MCP `tools/list` schema:

- local tool name = configured `tool-name`
- description = configured description if present, otherwise remote description
- parameters = translated JSON schema from the remote tool input schema

If the remote schema cannot be translated cleanly, startup should fail for that mapping instead of registering a misleading tool shape.

## Permissions And Visibility

`webSearch` must be governed by `ExecutionToolAccessPolicy`.

Design constraints:

- `DocumentToolAccessPolicy` continues to own only document-oriented tools
- `MemoryToolAccessPolicy` continues to own only memory-oriented tools
- external capability visibility is composed in `ExecutionToolAccessPolicy`

Recommended structure:

- add `DocumentToolNames.WEB_SEARCH = "webSearch"` as the canonical local name
- introduce `ExternalToolAccessPolicy`
- let `ExecutionToolAccessPolicy` merge document tools, memory tools, and external tools

Default exposure:

- `RESEARCH`: include `retrieveKnowledge` and `webSearch`
- main execution write roles: include existing write tools plus `webSearch`
- `REVIEW`: no `webSearch`
- `MEMORY`: no `webSearch`

这里不要把 `webSearch` 塞进 `DocumentToolAccessPolicy`，否则“文档能力策略”和“外部网络能力策略”会耦合，后面再接别的 MCP 工具时边界会越来越乱。

## Prompting Expectations

No runtime redesign is needed, but the relevant prompts should acknowledge the new capability:

- researcher prompt may use `webSearch` when the task depends on current public internet information
- main execution agent may use `webSearch` when the user request requires real-time facts not available in the document or local knowledge base
- prompts should clearly distinguish `webSearch` from `searchContent`

`searchContent` remains document-local search. `webSearch` is for current public internet information.

## Result Formatting

MCP tool-call results should be normalized into `ToolResult.message`.

Recommended formatting rules:

- if MCP returns `structuredContent`, preserve it
- if MCP returns text content blocks, preserve them
- return a single JSON string when structured data exists, for example:

```json
{
  "structuredContent": {},
  "text": "..."
}
```

- if only plain text is available, return plain text
- `updatedContent` remains `null` for `webSearch`

This keeps downstream model consumption simple and avoids inventing tool-specific rendering rules inside the runtime.

## Error Handling

Use the existing recoverable tool-error model.

Recoverable failures:

- missing API key or blank authorization header after configuration binding
- MCP initialize failure
- `tools/list` failure
- configured remote tool missing from the server
- `tools/call` transport failure
- remote JSON-RPC error response
- malformed MCP payload that prevents normal tool execution

These should throw `RecoverableToolException` so the runtime can feed the failure back to the model as a tool-result message.

Non-recoverable failures:

- local programming bugs
- invalid startup wiring
- local result serialization bugs

If MCP `tools/call` returns a normal tool result with an error flag or tool-level failure content, that should be returned as a normal `ToolResult.message` rather than escalated as infrastructure failure.

## Startup And Validation Behavior

The application should validate MCP-backed tool registration during startup for active servers.

Startup should fail when:

- server type is unsupported
- required fields such as `base-url` are blank
- no tools are configured for an active server
- a configured `remote-tool-name` is not present in the remote `tools/list`
- the remote input schema cannot be converted into a local `ToolSpecification`

This avoids silently booting with a partially broken real-time search capability.

## Testing Strategy

### Configuration tests

Add tests that prove:

- `agent.mcp` properties bind correctly
- placeholder-based headers are resolved through Spring configuration
- inactive servers do not register tools

### Client tests

Add tests for `StreamableHttpMcpClient` covering:

- initialize request is sent first
- `Mcp-Session-Id` is captured and reused
- `tools/list` request format is correct
- `tools/call` request format is correct
- HTTP and JSON-RPC failures are surfaced correctly

### Registration tests

Add tests that prove:

- active configured mappings become local `ToolHandler`s in `ToolRegistry`
- unsupported mappings fail fast
- local tool specification uses the configured local name

### Permission tests

Add tests for `ExecutionToolAccessPolicy` and any new `ExternalToolAccessPolicy` proving:

- `RESEARCH` includes `webSearch`
- main execution write roles include `webSearch`
- `REVIEW` excludes `webSearch`
- `MEMORY` excludes `webSearch`

### Tool execution tests

Add tests for `McpBackedToolHandler` proving:

- successful MCP responses become expected `ToolResult`
- tool-level remote errors remain model-visible results
- transport and protocol failures raise `RecoverableToolException`

### Runtime integration tests

Add focused runtime tests proving that:

- a model-issued `webSearch` tool call runs through the standard tool loop
- the result is appended to memory as a normal tool execution result
- recoverable MCP failures do not abort the loop

## Risks And Mitigations

- Risk: MCP protocol details vary between servers.
  Mitigation: support only `streamableHttp` in v1 and validate behavior at startup.

- Risk: remote schema translation may not map cleanly to `ToolSpecification`.
  Mitigation: fail fast for unsupported schema shapes instead of exposing incorrect tool contracts.

- Risk: network search gets overused where document search was intended.
  Mitigation: keep `webSearch` separate from `searchContent` in naming, permissions, and prompts.

- Risk: permission rules drift across tool categories.
  Mitigation: keep external tool visibility centralized in `ExecutionToolAccessPolicy` composition.

- Risk: MCP initialization failure blocks app startup unexpectedly.
  Mitigation: only validate active servers and make disablement explicit through configuration.

## Success Criteria

- the application can read MCP server configuration from `application.yml`
- an active Aliyun WebSearch MCP server is initialized and validated successfully
- `webSearch` is registered as a local tool in `ToolRegistry`
- `webSearch` is exposed only to researcher and main execution roles
- a normal tool-loop execution can call `webSearch` and receive model-visible results
- recoverable MCP failures surface through the existing tool-error path without breaking the runtime loop

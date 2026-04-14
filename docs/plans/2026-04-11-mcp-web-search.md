# MCP Web Search Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a reusable MCP integration layer and wire Aliyun Bailian WebSearch into the agent runtime as a locally visible `webSearch` tool.

**Architecture:** Keep the existing `ToolRegistry` and runtime loop unchanged at the boundary. Add a thin MCP adapter stack that reads `agent.mcp` configuration, creates a `streamableHttp` MCP client, validates configured remote tools at startup, and registers MCP-backed `ToolHandler` instances that are governed by `ExecutionToolAccessPolicy`.

**Tech Stack:** Spring Boot configuration properties, Spring HTTP client support, Jackson, LangChain4j `ToolSpecification`, JUnit 5, Mockito, existing tool runtime.

---

### Task 1: Add MCP configuration properties

**Files:**
- Create: `src/main/java/com/agent/editor/agent/mcp/config/McpProperties.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/config/McpServerProperties.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/config/McpToolProperties.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/config/McpPropertiesTest.java`

**Step 1: Write the failing tests**

Add `McpPropertiesTest` covering:

```java
@Test
void shouldBindActiveStreamableHttpServerAndToolMapping() {
    new ApplicationContextRunner()
            .withUserConfiguration(McpConfig.class)
            .withPropertyValues(
                    "agent.mcp.servers.web-search.type=streamableHttp",
                    "agent.mcp.servers.web-search.active=true",
                    "agent.mcp.servers.web-search.base-url=https://example.test/mcp",
                    "agent.mcp.servers.web-search.headers.Authorization=Bearer test-key",
                    "agent.mcp.servers.web-search.tools[0].tool-name=webSearch",
                    "agent.mcp.servers.web-search.tools[0].remote-tool-name=webSearch"
            )
            .run(context -> {
                McpProperties properties = context.getBean(McpProperties.class);
                assertThat(properties.getServers()).containsKey("web-search");
            });
}
```

Update `AgentConfigurationSplitTest` with a failing assertion that the application context exposes a single `McpProperties` bean.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpPropertiesTest,AgentConfigurationSplitTest test`

Expected: FAIL because `McpProperties` and related configuration classes do not exist yet.

**Step 3: Write minimal implementation**

Create Lombok bean classes for:

- `McpProperties`
  - `Map<String, McpServerProperties> servers`
- `McpServerProperties`
  - `String type`
  - `String description`
  - `boolean active`
  - `String name`
  - `String baseUrl`
  - `Map<String, String> headers`
  - `List<McpToolProperties> tools`
- `McpToolProperties`
  - `String toolName`
  - `String remoteToolName`
  - `String description`

Enable the properties in `TaskOrchestratorConfig` or the new MCP config class and add a disabled-by-default sample `agent.mcp.servers.web-search` block to `application.yml`.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpPropertiesTest,AgentConfigurationSplitTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/config src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/main/resources/application.yml src/test/java/com/agent/editor/config/McpPropertiesTest.java src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java
git commit -m "feat: add mcp configuration properties"
```

### Task 2: Implement streamable HTTP MCP client with initialize and session reuse

**Files:**
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpClient.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClient.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpJsonRpcRequest.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpJsonRpcResponse.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpInitializeResult.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpToolDescriptor.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/McpToolCallResult.java`
- Test: `src/test/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClientTest.java`

**Step 1: Write the failing tests**

Add `StreamableHttpMcpClientTest` using `MockRestServiceServer` or a similar Spring HTTP test facility.

Cover:

```java
@Test
void shouldInitializeBeforeListingToolsAndReuseSessionId() {
    // initialize response returns Mcp-Session-Id header
    // tools/list request must include the same header
}

@Test
void shouldCallRemoteToolWithConfiguredHeaders() {
    // tools/call request contains Authorization header and remote tool name
}

@Test
void shouldThrowRecoverableToolExceptionWhenJsonRpcReturnsError() {
    // tools/call error response becomes RecoverableToolException
}
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StreamableHttpMcpClientTest test`

Expected: FAIL because the client classes and protocol handling do not exist yet.

**Step 3: Write minimal implementation**

Implement `StreamableHttpMcpClient` with:

- lazy `initialize()` before first `tools/list` or `tools/call`
- cached `Mcp-Session-Id`
- JSON-RPC request bodies for:
  - `initialize`
  - `tools/list`
  - `tools/call`
- Spring HTTP calls using existing web dependencies
- concise Chinese comments around session reuse and initialization order

Keep v1 support narrow:

- support only `streamableHttp`
- parse only fields needed for tool discovery and tool invocation

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StreamableHttpMcpClientTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/client src/test/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClientTest.java
git commit -m "feat: add streamable http mcp client"
```

### Task 3: Add MCP-backed tool handler and result formatting

**Files:**
- Create: `src/main/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandler.java`
- Create: `src/main/java/com/agent/editor/agent/mcp/tool/McpToolResultFormatter.java`
- Test: `src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandlerTest.java`

**Step 1: Write the failing tests**

Add `McpBackedToolHandlerTest` covering:

```java
@Test
void shouldExposeConfiguredLocalToolNameAndRemoteSchemaAsToolSpecification() {
    // local tool name is webSearch even if remote metadata came from MCP
}

@Test
void shouldConvertStructuredContentAndTextIntoToolResultMessage() {
    // MCP structuredContent + text content becomes one ToolResult message
}

@Test
void shouldSurfaceRemoteToolLevelErrorAsNormalToolResult() {
    // tool-level failure does not become infrastructure exception
}
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolHandlerTest test`

Expected: FAIL because the MCP-backed handler and formatter do not exist yet.

**Step 3: Write minimal implementation**

Implement:

- `McpBackedToolHandler`
  - local name from `McpToolProperties.toolName`
  - remote call name from `remoteToolName`
  - local `ToolSpecification` built from remote schema
- `McpToolResultFormatter`
  - if `structuredContent` exists, return JSON with `structuredContent` and optional `text`
  - if only text exists, return text
  - always leave `updatedContent` as `null`

Use brief Chinese comments around:

- why local and remote tool names are intentionally decoupled
- why tool-level remote errors stay model-visible results instead of exceptions

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolHandlerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/tool src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandlerTest.java
git commit -m "feat: add mcp backed tool handler"
```

### Task 4: Register MCP-backed tools into the Spring tool registry

**Files:**
- Create: `src/main/java/com/agent/editor/config/McpConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Modify: `src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java`
- Test: `src/test/java/com/agent/editor/config/McpConfigTest.java`

**Step 1: Write the failing tests**

Add `McpConfigTest` covering:

```java
@Test
void shouldRegisterActiveMappedMcpToolIntoToolRegistry() {
    // stub MCP client returns a remote webSearch descriptor
    // ToolRegistry.get("webSearch") is not null
}

@Test
void shouldSkipInactiveServerMappings() {
    // inactive server does not register the tool
}
```

Update `AgentConfigurationSplitTest` with a failing assertion that the context can create `McpConfig`.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpConfigTest,AgentConfigurationSplitTest test`

Expected: FAIL because MCP-backed registration is not wired yet.

**Step 3: Write minimal implementation**

Implement `McpConfig` that:

- reads `McpProperties`
- creates `StreamableHttpMcpClient` for each active server
- loads `tools/list`
- validates configured `remote-tool-name`
- creates `McpBackedToolHandler` instances

Modify `ToolConfig` so the existing `ToolRegistry` bean also registers MCP-backed handlers.

Do not auto-register every remote MCP tool. Only register explicit configured mappings.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpConfigTest,AgentConfigurationSplitTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/McpConfig.java src/main/java/com/agent/editor/config/ToolConfig.java src/test/java/com/agent/editor/config/McpConfigTest.java src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java
git commit -m "feat: register mcp tools in tool registry"
```

### Task 5: Add external tool access policy and expose `webSearch` to the intended roles

**Files:**
- Create: `src/main/java/com/agent/editor/agent/tool/external/ExternalToolAccessPolicy.java`
- Modify: `src/main/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicy.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/tool/document/DocumentToolNames.java`
- Test: `src/test/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicyTest.java`
- Test: `src/test/java/com/agent/editor/agent/tool/external/ExternalToolAccessPolicyTest.java`

**Step 1: Write the failing tests**

Add `ExternalToolAccessPolicyTest` and update `ExecutionToolAccessPolicyTest` to cover:

```java
@Test
void shouldExposeWebSearchForResearchRole() {
    assertEquals(List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE, DocumentToolNames.WEB_SEARCH), ...);
}

@Test
void shouldAppendWebSearchForMainWriteRole() {
    // existing write tools + memory tools + webSearch in stable order
}

@Test
void shouldNotExposeWebSearchForReviewOrMemoryRole() {
    // review and memory remain unchanged
}
```

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionToolAccessPolicyTest,ExternalToolAccessPolicyTest test`

Expected: FAIL because there is no external tool policy and no `WEB_SEARCH` constant.

**Step 3: Write minimal implementation**

Implement `ExternalToolAccessPolicy` returning:

- `MAIN_WRITE` -> `List.of(DocumentToolNames.WEB_SEARCH)`
- `RESEARCH` -> `List.of(DocumentToolNames.WEB_SEARCH)`
- others -> `List.of()`

Update `ExecutionToolAccessPolicy` to merge external tools after document tools and before or after memory tools with one stable ordering rule. Add concise Chinese comments that explain why external tool visibility is composed here rather than in document policy.

Wire the new policy bean in `TaskOrchestratorConfig`.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionToolAccessPolicyTest,ExternalToolAccessPolicyTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/tool/external/ExternalToolAccessPolicy.java src/main/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicy.java src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java src/main/java/com/agent/editor/agent/tool/document/DocumentToolNames.java src/test/java/com/agent/editor/agent/tool/ExecutionToolAccessPolicyTest.java src/test/java/com/agent/editor/agent/tool/external/ExternalToolAccessPolicyTest.java
git commit -m "feat: add external tool access policy"
```

### Task 6: Update agent prompts and context tests for `webSearch`

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/supervisor/worker/ResearcherAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Test: `src/test/java/com/agent/editor/agent/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`

**Step 1: Write the failing tests**

Extend the prompt tests with assertions such as:

```java
assertTrue(systemMessage.text().contains(DocumentToolNames.WEB_SEARCH));
assertTrue(systemMessage.text().contains("real-time"));
assertTrue(systemMessage.text().contains("searchContent"));
```

For writer:

- mention `webSearch` only as a tool for current external facts
- keep `searchContent` clearly scoped to the current draft

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest test`

Expected: FAIL because prompts do not mention `webSearch` yet.

**Step 3: Write minimal implementation**

Update prompts so:

- researcher can use `webSearch` when current public internet information is needed
- writer can use `webSearch` only when the user task depends on real-time external facts
- prompts distinguish `webSearch` from document-local `searchContent`

Keep prompt edits narrow. Do not redesign the worker roles.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/supervisor/worker/ResearcherAgentContextFactory.java src/main/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactory.java src/test/java/com/agent/editor/agent/supervisor/worker/ResearcherAgentContextFactoryTest.java src/test/java/com/agent/editor/agent/supervisor/worker/GroundedWriterAgentContextFactoryTest.java
git commit -m "feat: add web search guidance to worker prompts"
```

### Task 7: Prove runtime execution and recoverable failure behavior

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/core/runtime/ToolLoopExecutionRuntimeTest.java`
- Test: `src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolRuntimeTest.java`

**Step 1: Write the failing tests**

Add focused tests proving:

```java
@Test
void shouldAppendWebSearchResultToToolMemoryWithoutUpdatedContent() {
    // runtime executes webSearch and stores normal tool result
}

@Test
void shouldContinueLoopAfterRecoverableMcpFailure() {
    // MCP transport/protocol failure becomes RecoverableToolException and loop continues
}
```

Stub the tool handler or MCP client as needed so the test isolates runtime behavior.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,McpBackedToolRuntimeTest test`

Expected: FAIL because runtime coverage for MCP-backed behavior does not exist yet.

**Step 3: Write minimal implementation**

Adjust only what the tests require:

- ensure `McpBackedToolHandler` throws `RecoverableToolException` for infrastructure failures
- ensure successful `webSearch` results return `updatedContent = null`
- keep runtime behavior unchanged unless the test reveals a real gap

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest,McpBackedToolRuntimeTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/core/runtime/ToolLoopExecutionRuntimeTest.java src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolRuntimeTest.java src/main/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandler.java src/main/java/com/agent/editor/agent/mcp/tool/McpToolResultFormatter.java
git commit -m "test: cover mcp web search runtime behavior"
```

### Task 8: Run focused verification and full regression check

**Files:**
- No code changes required unless regressions are found

**Step 1: Run focused MCP and policy tests**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpPropertiesTest,StreamableHttpMcpClientTest,McpBackedToolHandlerTest,McpConfigTest,ExecutionToolAccessPolicyTest,ExternalToolAccessPolicyTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,ToolLoopExecutionRuntimeTest,McpBackedToolRuntimeTest test
```

Expected: PASS

**Step 2: Run full test suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS

**Step 3: Commit final verification-only follow-up if needed**

If any last small fixes were required:

```bash
git add <affected files>
git commit -m "fix: finalize mcp web search integration"
```

If no additional fixes were needed, do not create an extra commit.

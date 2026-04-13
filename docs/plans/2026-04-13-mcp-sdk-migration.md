# MCP SDK Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the handwritten MCP JSON-RPC transport layer with LangChain4j MCP SDK transport/client while preserving the local `ToolRegistry`, explicit local tool mappings, and permission controls.

**Architecture:** Keep the application's MCP governance boundary intact: `McpConfig` still reads `agent.mcp`, `McpBackedToolHandler` still exposes locally named tools, and `ExecutionToolAccessPolicy` still governs visibility. Replace only the protocol/client ownership with `StreamableHttpMcpTransport` and `DefaultMcpClient`, using a thin local SDK adapter so the rest of the runtime remains unchanged.

**Tech Stack:** Spring Boot, LangChain4j `langchain4j-mcp` beta, Jackson, existing `ToolHandler` runtime, JUnit 5, Mockito.

---

### Task 1: Add MCP SDK dependency and replace handwritten client tests with SDK adapter tests

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapter.java`
- Modify: `src/main/java/com/agent/editor/agent/mcp/client/McpClient.java`
- Delete: `src/test/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClientTest.java`
- Create: `src/test/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapterTest.java`

**Step 1: Write the failing test**

Create `SdkMcpClientAdapterTest` with focused adapter-level tests such as:

```java
@Test
void shouldExposeSdkListedToolsAsLocalDescriptors() {
    dev.langchain4j.mcp.client.McpClient sdkClient = mock(dev.langchain4j.mcp.client.McpClient.class);
    when(sdkClient.listTools()).thenReturn(List.of(
            ToolSpecification.builder()
                    .name("webSearch")
                    .description("Search the public web")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("query")
                            .required("query")
                            .build())
                    .build()
    ));

    SdkMcpClientAdapter adapter = new SdkMcpClientAdapter(sdkClient, new ObjectMapper());

    assertThat(adapter.listTools())
            .extracting(McpToolDescriptor::getName)
            .containsExactly("webSearch");
}

@Test
void shouldWrapSdkExecutionFailuresAsRecoverableToolException() {
    dev.langchain4j.mcp.client.McpClient sdkClient = mock(dev.langchain4j.mcp.client.McpClient.class);
    when(sdkClient.executeTool(any())).thenThrow(new RuntimeException("timeout"));

    SdkMcpClientAdapter adapter = new SdkMcpClientAdapter(sdkClient, new ObjectMapper());

    assertThatThrownBy(() -> adapter.callTool("webSearch", "{\"query\":\"latest\"}"))
            .isInstanceOf(RecoverableToolException.class)
            .hasMessageContaining("timeout");
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SdkMcpClientAdapterTest test`

Expected: FAIL because the MCP SDK dependency and `SdkMcpClientAdapter` do not exist yet.

**Step 3: Write minimal implementation**

- Add dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mcp</artifactId>
    <version>1.11.0-beta19</version>
</dependency>
```

- Create `SdkMcpClientAdapter` that wraps `dev.langchain4j.mcp.client.McpClient`
- Keep local `McpClient` interface name for now, but change its implementation ownership from handwritten transport to SDK bridge
- Remove `StreamableHttpMcpClientTest`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SdkMcpClientAdapterTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add pom.xml src/main/java/com/agent/editor/agent/mcp/client/McpClient.java src/main/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapter.java src/test/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapterTest.java
git rm src/test/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClientTest.java
git commit -m "refactor: add mcp sdk adapter"
```

### Task 2: Rebuild `McpConfig` on SDK transport/client

**Files:**
- Modify: `src/main/java/com/agent/editor/config/McpConfig.java`
- Modify: `src/test/java/com/agent/editor/config/McpConfigTest.java`

**Step 1: Write the failing test**

Extend `McpConfigTest` with assertions that the config can build an SDK-backed client path:

```java
@Test
void shouldBuildSdkBackedClientForActiveStreamableHttpServer() {
    // subclass McpConfig and assert createClient returns SdkMcpClientAdapter
}
```

Also add a failure case:

```java
@Test
void shouldRejectUnsupportedServerType() {
    // active server type != streamableHttp should fail fast
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpConfigTest test`

Expected: FAIL because `McpConfig` still creates handwritten `StreamableHttpMcpClient`.

**Step 3: Write minimal implementation**

Update `McpConfig.createClient(...)` to:

- build `StreamableHttpMcpTransport`
- build `DefaultMcpClient`
- wrap it in `SdkMcpClientAdapter`

Suggested implementation shape:

```java
protected McpClient createClient(String serverKey,
                                 McpServerProperties serverProperties,
                                 ObjectMapper objectMapper) {
    validateServer(serverKey, serverProperties);
    StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
            .url(serverProperties.getBaseUrl())
            .customHeaders(serverProperties.getHeaders())
            .timeout(Duration.ofSeconds(30))
            .build();
    DefaultMcpClient sdkClient = DefaultMcpClient.builder()
            .key(serverKey)
            .clientName("ai-editor")
            .clientVersion("1.0.0")
            .protocolVersion("2025-06-18")
            .cacheToolList(true)
            .transport(transport)
            .build();
    return new SdkMcpClientAdapter(sdkClient, objectMapper);
}
```

Add concise Chinese comments around:

- why protocol ownership moves to the SDK here
- why explicit local tool mapping still remains in `McpConfig`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpConfigTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/McpConfig.java src/test/java/com/agent/editor/config/McpConfigTest.java
git commit -m "refactor: build mcp clients with sdk transport"
```

### Task 3: Simplify `McpBackedToolHandler` to use SDK `ToolSpecification`

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/mcp/client/McpToolDescriptor.java`
- Modify: `src/main/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandler.java`
- Modify: `src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandlerTest.java`

**Step 1: Write the failing test**

Update `McpBackedToolHandlerTest` so it no longer builds a raw JSON schema node. Instead, make it use SDK `ToolSpecification`:

```java
McpToolDescriptor descriptor = new McpToolDescriptor(
        ToolSpecification.builder()
                .name("webSearch")
                .description("Remote tool description")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Search query")
                        .required("query")
                        .build())
                .build()
);
```

Add assertion that:

- local name is still overridden to `webSearch`
- local description can still override remote description
- parameters are taken from the SDK tool spec directly

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolHandlerTest test`

Expected: FAIL because `McpToolDescriptor` and `McpBackedToolHandler` still expect handwritten schema nodes.

**Step 3: Write minimal implementation**

- Change `McpToolDescriptor` to carry SDK `ToolSpecification` or expose its fields from one
- Update `McpBackedToolHandler.specification()` to:
  - copy SDK `ToolSpecification`
  - override local `name`
  - override description only when configured
- remove handwritten JSON schema translation helpers from `McpBackedToolHandler`

This is the point where handwritten schema translation code should disappear.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolHandlerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/client/McpToolDescriptor.java src/main/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandler.java src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandlerTest.java
git commit -m "refactor: use sdk tool specifications for mcp handlers"
```

### Task 4: Normalize SDK `ToolExecutionResult` into local MCP result objects

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/mcp/client/McpToolCallResult.java`
- Modify: `src/main/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapter.java`
- Modify: `src/main/java/com/agent/editor/agent/mcp/tool/McpToolResultFormatter.java`
- Modify: `src/test/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapterTest.java`

**Step 1: Write the failing test**

Extend `SdkMcpClientAdapterTest` to cover:

```java
@Test
void shouldConvertSdkStructuredResultAndTextIntoNormalizedCallResult() {
    ToolExecutionResult sdkResult = ToolExecutionResult.builder()
            .result(Map.of("items", List.of(Map.of("title", "news"))))
            .resultText("result text")
            .build();
    when(sdkClient.executeTool(any())).thenReturn(sdkResult);

    McpToolCallResult result = adapter.callTool("webSearch", "{\"query\":\"latest\"}");

    assertThat(result.getStructuredContent()).isNotNull();
    assertThat(result.getText()).isEqualTo("result text");
}
```

Also add a test for `isError=true` preserving model-visible error state.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SdkMcpClientAdapterTest test`

Expected: FAIL because the adapter does not yet normalize SDK `ToolExecutionResult` fully.

**Step 3: Write minimal implementation**

In `SdkMcpClientAdapter.callTool(...)`:

- create `ToolExecutionRequest`
- call SDK `executeTool(...)`
- normalize:
  - `isError()`
  - `resultText()`
  - `result()` serialized through Jackson when present

Keep `McpToolResultFormatter` input contract stable:

- `boolean error`
- `Object structuredContent`
- `String text`

Use a brief Chinese comment around the error boundary:

- tool-level errors remain model-visible results
- SDK/client failures become `RecoverableToolException`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SdkMcpClientAdapterTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/client/McpToolCallResult.java src/main/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapter.java src/main/java/com/agent/editor/agent/mcp/tool/McpToolResultFormatter.java src/test/java/com/agent/editor/agent/mcp/client/SdkMcpClientAdapterTest.java
git commit -m "refactor: normalize sdk mcp execution results"
```

### Task 5: Remove handwritten MCP protocol classes and stale references

**Files:**
- Delete: `src/main/java/com/agent/editor/agent/mcp/client/McpJsonRpcRequest.java`
- Delete: `src/main/java/com/agent/editor/agent/mcp/client/McpJsonRpcResponse.java`
- Delete: `src/main/java/com/agent/editor/agent/mcp/client/McpInitializeResult.java`
- Delete: `src/main/java/com/agent/editor/agent/mcp/client/StreamableHttpMcpClient.java`
- Modify: any remaining imports or tests that still reference these classes

**Step 1: Write the failing safety check**

Add a small guard test or use a targeted source search command as the red step:

Run:

```bash
rg -n "McpJsonRpcRequest|McpJsonRpcResponse|McpInitializeResult|StreamableHttpMcpClient" src/main/java src/test/java
```

Expected: matches still exist before cleanup.

**Step 2: Remove the handwritten classes and references**

Delete the classes and update any imports/tests to the new SDK adapter.

**Step 3: Run targeted verification**

Run:

```bash
rg -n "McpJsonRpcRequest|McpJsonRpcResponse|McpInitializeResult|StreamableHttpMcpClient" src/main/java src/test/java
```

Expected: no matches

Then run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SdkMcpClientAdapterTest,McpConfigTest,McpBackedToolHandlerTest test
```

Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/com/agent/editor/agent/mcp/client src/test/java/com/agent/editor/agent/mcp/client src/test/java/com/agent/editor/config/McpConfigTest.java src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolHandlerTest.java
git commit -m "refactor: remove handwritten mcp protocol layer"
```

### Task 6: Re-verify runtime and integration behavior on SDK-backed MCP path

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolRuntimeTest.java`
- Modify: `src/test/java/com/agent/editor/config/McpConfigTest.java` if the runtime assertions need SDK-shaped descriptors

**Step 1: Write the failing test update**

Adjust `McpBackedToolRuntimeTest` so the test helper builds `McpToolDescriptor` from SDK `ToolSpecification`, not handwritten JSON schema nodes.

Keep assertions for:

- tool result stored in transcript
- no `updatedContent`
- recoverable SDK failure does not break the tool loop

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolRuntimeTest test`

Expected: FAIL if helper/test fixtures still depend on old descriptor shape.

**Step 3: Write minimal implementation**

Update only the test fixtures or any small production adapters needed so runtime tests use the SDK-based descriptor path.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpBackedToolRuntimeTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/mcp/tool/McpBackedToolRuntimeTest.java src/test/java/com/agent/editor/config/McpConfigTest.java
git commit -m "test: align mcp runtime coverage with sdk bridge"
```

### Task 7: Run focused regression suite for MCP SDK migration

**Files:**
- No code changes required unless regressions are found

**Step 1: Run focused suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=McpPropertiesTest,SdkMcpClientAdapterTest,McpBackedToolHandlerTest,McpConfigTest,ExecutionToolAccessPolicyTest,ExternalToolAccessPolicyTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,ToolLoopExecutionRuntimeTest,McpBackedToolRuntimeTest test
```

Expected: PASS

**Step 2: Fix any focused regressions and commit if needed**

If fixes are required:

```bash
git add <affected files>
git commit -m "fix: resolve mcp sdk migration regressions"
```

If not, do not create an extra commit.

### Task 8: Run full project verification

**Files:**
- No code changes required unless full-suite regressions are found

**Step 1: Run full test suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS

**Step 2: If full-suite regressions appear, fix them minimally**

Only touch files necessary to restore compatibility with the SDK-backed MCP bridge.

**Step 3: Commit final compatibility fixes if needed**

```bash
git add <affected files>
git commit -m "fix: finalize mcp sdk migration"
```

If no fixes were needed, do not create an extra commit.

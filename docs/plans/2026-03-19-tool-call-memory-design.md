# Tool Call Memory Design

**Goal:** Preserve both AI tool-call requests and tool execution results in execution memory so LangChain4j sees a complete tool interaction history.

**Approach:** Represent tool-call requests as a dedicated execution-memory message that carries structured `ToolCall` data. Map that message to a LangChain4j `AiMessage` with `ToolExecutionRequest` payloads, then keep tool results as `ToolExecutionResultMessage`.

**Data Flow:**
- Model returns `Decision.ToolCalls`
- Runtime appends one AI tool-call memory message for the requested calls
- Runtime executes tools and appends one tool-result memory message per call
- Mapper converts memory into:
  - `AiMessage(text, toolExecutionRequests)`
  - `ToolExecutionResultMessage(id, toolName, text)`

**Why:** Storing only tool results loses the causal link that the model first requested a tool. Storing tool calls as plain text would repeat the same ambiguity problem as using `UserMessage` for tool results.

**Files:**
- Modify `src/main/java/com/agent/editor/agent/v2/core/state/ExecutionMessage.java`
- Modify `src/main/java/com/agent/editor/agent/v2/mapper/ExecutionMemoryChatMessageMapper.java`
- Modify `src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java`
- Test `src/test/java/com/agent/editor/agent/v2/react/ExecutionMemoryChatMessageMapperTest.java`
- Test `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`
- Test `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`

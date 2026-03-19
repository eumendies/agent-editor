# Tool Call Memory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep both tool-call requests and tool execution results in execution memory so later model turns receive a correct LangChain4j chat history.

**Architecture:** Add a structured execution-memory message for AI tool calls, map it to `AiMessage` with `ToolExecutionRequest`, and have the runtime append that message before appending tool result messages. This keeps the memory protocol aligned with LangChain4j without encoding tool calls as plain text.

**Tech Stack:** Java 17, Spring Boot, LangChain4j 1.11.0, JUnit 5

---

### Task 1: Add failing mapper tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ExecutionMemoryChatMessageMapperTest.java`

**Step 1: Write the failing test**
- Add a test that builds transcript memory with an AI tool-call execution message and asserts the mapper returns `AiMessage` with `hasToolExecutionRequests() == true`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionMemoryChatMessageMapperTest test`

**Step 3: Write minimal implementation**
- Add the new execution-memory message type and mapper branch.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionMemoryChatMessageMapperTest test`

### Task 2: Add failing runtime tests

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntimeTest.java`

**Step 1: Write the failing test**
- Assert that runtime memory contains an AI tool-call message before tool-result messages after a tool-using iteration.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DefaultExecutionRuntimeTest test`

**Step 3: Write minimal implementation**
- Update runtime to append the AI tool-call execution message before executing tools.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DefaultExecutionRuntimeTest test`

### Task 3: Regression verification

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`

**Step 1: Update regression coverage**
- Keep the existing transcript-memory test aligned with the new AI tool-call execution message shape.

**Step 2: Run focused tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ExecutionMemoryChatMessageMapperTest,DefaultExecutionRuntimeTest,ReactAgentDefinitionTest test`

**Step 3: Run full verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

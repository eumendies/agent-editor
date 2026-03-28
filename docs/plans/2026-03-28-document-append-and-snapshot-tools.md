# Document Append And Snapshot Tools Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `appendToDocument` and `getDocumentSnapshot` as first-class v2 document tools, expose them to the relevant agents, and verify the runtime can use them correctly.

**Architecture:** Keep the existing `ToolHandler` / `ToolContext` / `ToolResult` contract. Add two new handlers under the document tool package, register them in Spring config, then extend the relevant agent-visible tool lists and prompt guidance. Verify behavior with focused tool tests and nearby orchestration/runtime tests.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, JUnit 5, Maven

---

### Task 1: Add red tests for the two new document tools

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/AppendToDocumentToolTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/GetDocumentSnapshotToolTest.java`

**Step 1: Write the failing tests**

Cover:

- append exact raw text to existing content
- append to empty/null current content
- reject missing append content
- snapshot returns current content without mutation
- specification names are `appendToDocument` and `getDocumentSnapshot`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AppendToDocumentToolTest,GetDocumentSnapshotToolTest test`

Expected: FAIL because the tool classes do not exist yet.

**Step 3: Write minimal implementation**

Create:

- `src/main/java/com/agent/editor/agent/v2/tool/document/AppendToDocumentTool.java`
- `src/main/java/com/agent/editor/agent/v2/tool/document/GetDocumentSnapshotTool.java`

Implement minimal JSON parsing and `ToolResult` behavior consistent with existing document tools.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AppendToDocumentToolTest,GetDocumentSnapshotToolTest test`

Expected: PASS

### Task 2: Register the tools and prove they are visible in the registry

**Files:**
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/ToolRegistryTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing test**

Add assertions that the Spring-created `ToolRegistry` contains:

- `appendToDocument`
- `getDocumentSnapshot`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolRegistryTest,AgentV2ConfigurationSplitTest test`

Expected: FAIL until registration is added.

**Step 3: Write minimal implementation**

Register the two handlers in `ToolConfig` alongside the existing document tools.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolRegistryTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

### Task 3: Expose the tools to document-writing agents and update prompt guidance

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: relevant supervisor worker tests:
  - `src/test/java/com/agent/editor/agent/v2/react/ReactAgentTest.java`
  - `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentTest.java`
  - nearby worker/supervisor tests if allowed tool lists are asserted there

**Step 1: Write the failing tests**

Add assertions that:

- React prompt mentions `appendToDocument` vs `editDocument`
- relevant tool lists include `appendToDocument` and `getDocumentSnapshot`
- supervisor editor-style worker paths still expose the correct editing tool set

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,GroundedWriterAgentTest,SupervisorOrchestratorTest test`

Expected: FAIL until prompt/tool-list changes are wired.

**Step 3: Write minimal implementation**

Update prompt text and allowed tool lists only where document-writing semantics apply.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,GroundedWriterAgentTest,SupervisorOrchestratorTest test`

Expected: PASS

### Task 4: Prove runtime behavior for append + latest snapshot in one tool loop

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Write the failing test**

Add a test where one tool updates content and a following tool reads the latest snapshot in the same loop/runtime path.

Suggested shape:

- first tool call: `appendToDocument`
- second tool call: `getDocumentSnapshot`
- verify second tool receives the content after append

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest test`

Expected: FAIL until the new handlers are registered in the test registry and behavior is asserted correctly.

**Step 3: Write minimal implementation**

If the runtime already supports this correctly, only add the handlers / test setup needed. Do not change runtime code unless the test proves a real gap.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ToolLoopExecutionRuntimeTest test`

Expected: PASS

### Task 5: Run targeted regression verification

**Files:**
- Modify: any files touched in Tasks 1-4

**Step 1: Run focused suite**

Run:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AppendToDocumentToolTest,GetDocumentSnapshotToolTest,ToolRegistryTest,AgentV2ConfigurationSplitTest,ToolLoopExecutionRuntimeTest,ReactAgentTest,GroundedWriterAgentTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 2: Run nearby broader suite**

Run:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=PlanningThenExecutionOrchestratorTest,PlanningAgentContextFactoryTest,ToolLoopExecutionRuntimeTest,ReactAgentTest,ReflexionOrchestratorTest,ReflexionCriticDefinitionTest,GroundedWriterAgentTest,SupervisorOrchestratorTest test`

Expected: PASS

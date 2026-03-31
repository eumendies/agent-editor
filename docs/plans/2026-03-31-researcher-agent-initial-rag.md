# Researcher Agent Initial RAG Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `ResearcherAgent` always issue a deterministic first `retrieveKnowledge` call using the raw user instruction, then let the model decide whether to continue retrieval or complete.

**Architecture:** Keep the behavior change local to `ResearcherAgent`. Add an early branch that detects whether any executed `retrieveKnowledge` result already exists in transcript memory; if not, return a fixed `ToolLoopDecision.ToolCalls`. Once at least one retrieval result exists, preserve the current model-driven loop and structured completion parsing.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito-free unit tests around `ChatModel`

---

### Task 1: Lock Down First-Turn Retrieval Behavior

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`

**Step 1: Write the failing test**

Add a test that builds a context whose memory only contains the current user instruction and asserts:

```java
@Test
void shouldReturnDeterministicInitialRetrieveKnowledgeCall() {
    RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
            .aiMessage(AiMessage.from("{}"))
            .build());
    ResearcherAgent definition = new ResearcherAgent(chatModel);

    ToolLoopDecision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
            new ChatMessage.UserChatMessage("ground this answer")
    ))));

    ToolLoopDecision.ToolCalls toolCalls = assertInstanceOf(ToolLoopDecision.ToolCalls.class, decision);
    assertEquals(1, toolCalls.getCalls().size());
    assertEquals("retrieveKnowledge", toolCalls.getCalls().get(0).getName());
    assertEquals("{\"query\":\"ground this answer\"}", toolCalls.getCalls().get(0).getArguments());
    assertNull(chatModel.lastRequest);
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest#shouldReturnDeterministicInitialRetrieveKnowledgeCall test`

Expected: FAIL because `ResearcherAgent` still invokes the model instead of returning a deterministic tool call.

**Step 3: Write minimal implementation**

Add a pre-model branch in `ResearcherAgent.decide(...)`:

```java
if (shouldRunInitialInstructionRetrieval(context)) {
    return initialInstructionRetrieval(context);
}
```

Implement helpers that:

- scan transcript memory for `ToolExecutionResultChatMessage` with tool name `retrieveKnowledge`
- build a single `ToolCall` whose arguments are a JSON object containing the raw instruction

Add a brief Chinese comment on the branch explaining that the first retrieval must preserve the original user query before any model-led rewrite.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest#shouldReturnDeterministicInitialRetrieveKnowledgeCall test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java
git commit -m "feat: lock researcher initial retrieval"
```

### Task 2: Preserve Model-Driven Later Turns

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`

**Step 1: Write the failing test**

Add or adjust a test so a context with an executed `retrieveKnowledge` result in memory still invokes the model:

```java
@Test
void shouldInvokeModelAfterInitialRetrievalResultExists() {
    RecordingChatModel chatModel = new RecordingChatModel(ChatResponse.builder()
            .aiMessage(AiMessage.from("""
                    {"queries":["rewritten"],"evidenceSummary":"...", "limitations":"...", "uncoveredPoints":[], "chunks":[]}
                    """))
            .build());
    ResearcherAgent definition = new ResearcherAgent(chatModel);

    ToolLoopDecision decision = definition.decide(context(List.of(retrieveKnowledgeTool()), new ChatTranscriptMemory(List.of(
            new ChatMessage.UserChatMessage("ground this answer"),
            new ChatMessage.ToolExecutionResultChatMessage(
                    "tool-1",
                    "retrieveKnowledge",
                    "{\"query\":\"ground this answer\"}",
                    "[{\"chunkText\":\"supports supervisor\"}]"
            ),
            new ChatMessage.UserChatMessage("ground this answer")
    ))));

    assertInstanceOf(ToolLoopDecision.Complete.class, decision);
    assertNotNull(chatModel.lastRequest);
}
```

Also adjust any existing tests that assumed first-turn model invocation so they include a prior retrieval result in memory.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest#shouldInvokeModelAfterInitialRetrievalResultExists test`

Expected: FAIL if the new first-turn logic is too broad and suppresses later model turns.

**Step 3: Write minimal implementation**

Tighten the retrieval-history predicate so only executed `retrieveKnowledge` results count as “initial retrieval already completed”. Keep the rest of the current model flow unchanged.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest test`

Expected: PASS for the full `ResearcherAgentTest` suite.

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java
git commit -m "test: cover researcher post-retrieval flow"
```

### Task 3: Final Verification

**Files:**
- Verify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`

**Step 1: Run focused regression tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest,ResearcherAgentContextFactoryTest test`

Expected: PASS

**Step 2: Inspect diff**

Run: `git diff -- src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`

Expected: Diff only shows the first-turn deterministic retrieval logic, the Chinese intent comment, and test updates required by the new behavior.

**Step 3: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java \
        docs/plans/2026-03-31-researcher-agent-initial-rag.md
git commit -m "feat: enforce researcher initial rag retrieval"
```

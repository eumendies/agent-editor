# Structured Completion Parsing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Harden supervisor worker structured output parsing so reviewer markdown-fenced JSON can still be parsed and shared parsing logic is centralized.

**Architecture:** Keep worker class structure unchanged, add a shared parsing utility for structured completion outputs, and tighten the reviewer prompt so the model is instructed to emit raw JSON only. Researcher and reviewer will both consume the shared parser.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito-style test doubles, Jackson

---

### Task 1: Add failing tests for reviewer markdown-fence handling

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`

**Step 1: Write the failing test**

- Add a reviewer agent test where the model returns:
  ```json
  ```json
  {...ReviewerFeedback...}
  ```
  ```
- Assert the decision still completes with a typed `ReviewerFeedback`.
- Tighten the prompt test to assert the system prompt tells the model not to wrap JSON in markdown fences or backticks.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=EvidenceReviewerAgentTest,EvidenceReviewerAgentContextFactoryTest test`

Expected: reviewer fenced JSON parsing fails and prompt assertion fails until implementation is added.

### Task 2: Add failing tests for shared parser utility

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/StructuredCompletionParsersTest.java`

**Step 1: Write the failing test**

- Add tests for:
  - direct JSON parse success
  - fenced JSON parse success after cleanup
  - non-JSON text returning `null`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredCompletionParsersTest test`

Expected: compilation or test failure because the utility does not exist yet.

### Task 3: Implement shared structured completion parser

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/StructuredCompletionParsers.java`

**Step 1: Write minimal implementation**

- Add a Jackson-based utility with:
  - `parseJson(String text, Class<T> type)`
  - `parseJsonWithMarkdownCleanup(String text, Class<T> type)`
  - internal fence stripping for full fenced blocks such as ```` ```json ... ``` ````

**Step 2: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredCompletionParsersTest test`

Expected: PASS

### Task 4: Wire workers and prompt to the shared parser

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`

**Step 1: Write minimal implementation**

- Replace in-agent duplicated Jackson parsing with the shared parser utility.
- For reviewer, use the markdown-cleanup retry path.
- For researcher, also use the shared parser to keep behavior consistent.
- Tighten reviewer prompt with a raw-JSON-only instruction.

**Step 2: Run targeted tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=EvidenceReviewerAgentTest,EvidenceReviewerAgentContextFactoryTest,ResearcherAgentTest,StructuredCompletionParsersTest test`

Expected: PASS

### Task 5: Run related regression tests

**Files:**
- Verify only

**Step 1: Run regression suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,StructuredCompletionParsersTest,ToolLoopExecutionRuntimeTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

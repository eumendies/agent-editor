# Structured Output Parser Relocation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Relocate structured output parsing to a shared `agent/v2` utility and replace duplicated JSON parsing logic across supervisor and reflexion agents.

**Architecture:** Introduce a general-purpose `StructuredOutputParsers` utility in `agent/v2/util`, use it for strict and tolerant parse paths, and remove supervisor-local parsing helpers. Reflexion and supervisor code paths will share the same markdown-fence cleanup behavior.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Jackson

---

### Task 1: Add failing tests for relocated parser and reflexion fence handling

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinitionTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/util/StructuredOutputParsersTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`

**Step 1: Write the failing test**

- Add a reflexion critic test where the model returns fenced JSON and assert it still parses to `ReflexionCritique`.
- Add a strict parse test that fenced JSON is accepted by the strict parsing entrypoint.
- Add a supervisor routing test where the routing response is wrapped in fenced JSON and assert the chosen worker still maps through.
- Add utility tests in the new util package.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest,HybridSupervisorAgentTest,StructuredOutputParsersTest test`

Expected: failure because the shared util does not exist in the new package and reflexion/routing do not yet use tolerant fenced parsing.

### Task 2: Implement shared utility and remove supervisor-local utility

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/util/StructuredOutputParsers.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/StructuredCompletionParsers.java`

**Step 1: Write minimal implementation**

- Add shared strict and tolerant parsing helpers backed by Jackson.
- Keep markdown fence cleanup conservative and full-wrapper-only.

**Step 2: Run utility tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredOutputParsersTest test`

Expected: PASS

### Task 3: Replace duplicated parsing in supervisor and reflexion

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCritic.java`

**Step 1: Write minimal implementation**

- Replace local parser calls/imports with the shared util.
- Use tolerant parse for `tryParse...` style paths.
- Use strict parse for `ReflexionCritic.parseCritique(...)`.
- Remove no-longer-needed local `ObjectMapper` fields where possible.

**Step 2: Run targeted tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReflexionCriticDefinitionTest,HybridSupervisorAgentTest,EvidenceReviewerAgentTest,ResearcherAgentTest,StructuredOutputParsersTest test`

Expected: PASS

### Task 4: Run regression tests

**Files:**
- Verify only

**Step 1: Run regression suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,ResearcherAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,StructuredOutputParsersTest,ToolLoopExecutionRuntimeTest,HybridSupervisorAgentTest,ReflexionCriticDefinitionTest,ReflexionCriticContextFactoryTest,ReflexionOrchestratorTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

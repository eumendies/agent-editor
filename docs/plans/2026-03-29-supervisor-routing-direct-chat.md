# Supervisor Routing Direct Chat Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace supervisor routing `AiService` usage with direct `ChatModel` requests while moving routing prompt assembly and response-format setup into `SupervisorContextFactory`.

**Architecture:** `SupervisorContextFactory` will build the full routing `ModelInvocationContext`, including one `SystemMessage`, one `UserMessage`, one `AiMessage` per historical `WorkerResult`, and the structured response format. `HybridSupervisorAgent` will become a thin caller that requests the model, parses `SupervisorRoutingResponse`, and preserves all existing fallback and decision rules.

**Tech Stack:** Java 17, Spring Boot, Lombok, JUnit 5, LangChain4j

---

### Task 1: Lock In Routing Context Shape

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`

**Step 1: Write the failing test**

Update `shouldBuildRoutingInvocationContextWithCandidateAndWorkerSummaries` to assert:
- the routing invocation now contains a `SystemMessage`
- the second message is a `UserMessage`
- each `SupervisorContext.WorkerResult` becomes its own `AiMessage`
- the invocation includes a non-null `responseFormat`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest test`

Expected: FAIL because the factory still emits a single `UserMessage` and no structured response format.

**Step 3: Write minimal implementation**

Modify `SupervisorContextFactory` so `buildRoutingInvocationContext(...)`:
- builds one `SystemMessage`
- builds one `UserMessage` with task, content, and candidates
- appends one `AiMessage` per worker result
- configures the routing `responseFormat`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java
git commit -m "test: lock supervisor routing context shape"
```

### Task 2: Switch HybridSupervisorAgent To Direct ChatModel Calls

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java`
- Reference: `src/main/java/com/agent/editor/agent/v2/react/ReactAgent.java`

**Step 1: Write the failing test**

Update `HybridSupervisorAgentTest` to:
- replace `SupervisorRoutingAiService`-based tests with `ChatModel`-based tests
- verify JSON text returned by the stub `ChatModel` is parsed into `SupervisorRoutingResponse`
- verify agent fallback still happens when the model returns an out-of-candidate worker

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=HybridSupervisorAgentTest test`

Expected: FAIL because `HybridSupervisorAgent` still depends on `SupervisorRoutingAiService`.

**Step 3: Write minimal implementation**

Modify `HybridSupervisorAgent` to:
- store `ChatModel` instead of `SupervisorRoutingAiService`
- request a `ModelInvocationContext` from `SupervisorContextFactory`
- build and send a `ChatRequest`
- parse `response.aiMessage().text()` into `SupervisorRoutingResponse`
- keep all existing rule-based completion and fallback behavior unchanged

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=HybridSupervisorAgentTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java
git commit -m "refactor: use direct chat model for supervisor routing"
```

### Task 3: Remove AiService Artifacts And Fix Wiring

**Files:**
- Delete: `src/main/java/com/agent/editor/agent/v2/supervisor/routing/SupervisorRoutingAiService.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`

**Step 1: Write the failing test**

Update configuration coverage to assert:
- `HybridSupervisorAgent` still wires with the shared `SupervisorContextFactory`
- no configuration path relies on `SupervisorRoutingAiService`

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest test`

Expected: FAIL if any wiring or compilation path still references `SupervisorRoutingAiService`.

**Step 3: Write minimal implementation**

Remove the obsolete ai-service file and any leftover imports or constructors that depend on it. Keep Spring wiring aligned with the direct `ChatModel` constructor.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,HybridSupervisorAgentTest,SupervisorContextFactoryTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/SupervisorAgentConfig.java src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java src/main/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgent.java src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java
git rm src/main/java/com/agent/editor/agent/v2/supervisor/routing/SupervisorRoutingAiService.java
git commit -m "refactor: remove supervisor routing ai service"
```

### Task 4: Run Targeted Supervisor Regression Verification

**Files:**
- Modify: none unless regressions appear
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/routing/HybridSupervisorAgentTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Run the targeted regression suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SupervisorContextFactoryTest,HybridSupervisorAgentTest,AgentV2ConfigurationSplitTest test
```

Expected: PASS

**Step 2: Fix any routing-path regressions**

If failures appear, make the minimal change in the touched supervisor routing files only.

**Step 3: Re-run the targeted regression suite**

Run the same command again until green.

**Step 4: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor src/main/java/com/agent/editor/agent/v2/supervisor/routing src/main/java/com/agent/editor/config src/test/java/com/agent/editor/agent/v2/supervisor src/test/java/com/agent/editor/agent/v2/supervisor/routing src/test/java/com/agent/editor/config docs/plans/2026-03-29-supervisor-routing-direct-chat-design.md docs/plans/2026-03-29-supervisor-routing-direct-chat.md
git commit -m "refactor: centralize supervisor routing context assembly"
```

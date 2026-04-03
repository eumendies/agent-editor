# Document Tool Mode Switch Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Route every `agent/v2` document-reading or document-writing path through a shared token-threshold policy so small documents keep whole-document tools and long documents expose only incremental tools.

**Architecture:** Add a new configuration-backed `DocumentToolAccessPolicy` that uses `StructuredDocumentService` to classify the current document as `FULL` or `INCREMENTAL`, then resolve role-based allowed tools for `WRITE`, `REVIEW`, and `RESEARCH`. Wire that policy into `ReActAgentOrchestrator`, `ReflexionOrchestrator`, and `SupervisorOrchestrator`, and update prompts so each context factory only describes the tools actually visible in the current execution context.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, Lombok, JUnit 5, Maven

---

### Task 1: Add red tests for document tool mode properties and access policy

**Files:**
- Create: `src/test/java/com/agent/editor/config/DocumentToolModePropertiesTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicyTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing properties binding test**

Cover binding for:

- `agent.document-tool-mode.long-document-threshold-tokens`

Suggested assertion shape:

```java
assertThat(properties.getLongDocumentThresholdTokens()).isEqualTo(4321);
```

**Step 2: Write the failing access policy tests**

Cover:

- small document returns `FULL`
- large document returns `INCREMENTAL`
- `WRITE` returns whole-document tools in full mode
- `WRITE` returns node read and patch tools in incremental mode
- `REVIEW` switches between `getDocumentSnapshot` and `readDocumentNode`
- `RESEARCH` keeps retrieval-only tools

Use real `StructuredDocumentService` so the test exercises the same `estimatedTokens` path used at runtime.

**Step 3: Extend configuration wiring tests**

Assert that Spring wires:

- `DocumentToolModeProperties`
- `DocumentToolAccessPolicy`

and still keeps both whole-document and incremental tools in `ToolRegistry`.

**Step 4: Run focused tests to verify they fail**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentToolModePropertiesTest,DocumentToolAccessPolicyTest,AgentV2ConfigurationSplitTest test
```

Expected: FAIL because the new properties class and access policy do not exist yet.

**Step 5: Write the minimal implementation**

Create:

- `src/main/java/com/agent/editor/config/DocumentToolModeProperties.java`
- `src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicy.java`

Add any small enums the policy needs, such as:

- `DocumentToolMode`
- `DocumentToolAccessRole`

Register the properties bean in `TaskOrchestratorConfig` or another existing configuration entry point already responsible for runtime wiring.

**Step 6: Re-run the focused tests**

Use the same command from Step 4.

Expected: PASS.

**Step 7: Commit the policy milestone**

```bash
git add src/main/java/com/agent/editor/config/DocumentToolModeProperties.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicy.java \
        src/test/java/com/agent/editor/config/DocumentToolModePropertiesTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicyTest.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java \
        src/main/resources/application.yml
git commit -m "feat: add document tool access policy"
```

### Task 2: Add red orchestrator tests for dynamic allowed tool resolution

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Write failing ReAct orchestrator tests**

Add tests that verify:

- small documents pass full-mode write tools into `ExecutionRequest.allowedTools`
- long documents pass incremental-mode write tools into `ExecutionRequest.allowedTools`

You will likely need a recording runtime stub that captures the request passed by `ReActAgentOrchestrator`.

**Step 2: Write failing reflexion orchestrator tests**

Add tests that verify:

- actor uses `WRITE` resolution
- critic uses `REVIEW` resolution
- long documents remove `getDocumentSnapshot` from both actor and critic requests

**Step 3: Write failing supervisor orchestrator tests**

Add tests that verify:

- writer worker resolves `WRITE`
- reviewer worker resolves `REVIEW`
- researcher worker remains retrieval-only
- long documents no longer expose whole-document tools to writer or reviewer

Reuse the existing recording runtime harnesses where possible instead of introducing new fake infrastructure.

**Step 4: Run focused tests to verify they fail**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=SingleAgentOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest test
```

Expected: FAIL because orchestrators still use static or empty allowed tool lists.

### Task 3: Implement dynamic tool selection in orchestrators and runtime wiring

**Files:**
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/context/SupervisorContext.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Thread the policy into orchestrator construction**

Update bean wiring so:

- `ReActAgentOrchestrator`
- `ReflexionOrchestrator`
- `SupervisorOrchestrator`

all receive the shared `DocumentToolAccessPolicy`.

**Step 2: Update ReAct execution request creation**

Resolve `WRITE` tools from the current request document before constructing `ExecutionRequest`.

**Step 3: Replace reflexion static tool lists**

Remove the current `ACTOR_ALLOWED_TOOLS` and `CRITIC_ALLOWED_TOOLS` constants.

At request creation time:

- actor request resolves `WRITE`
- critic request resolves `REVIEW`

**Step 4: Move supervisor worker tool choice to runtime resolution**

Keep worker identity and capabilities in `WorkerRegistry`, but stop relying on static document tool lists there for writer and reviewer dispatch.

The implementation should map worker kinds to roles:

- writer -> `WRITE`
- reviewer -> `REVIEW`
- researcher -> existing static retrieval tool list

If updating `SupervisorContext.WorkerDefinition` is necessary, keep the change minimal and reviewable.

**Step 5: Re-run the focused orchestrator tests**

Use the same command from Task 2 Step 4.

Expected: PASS.

**Step 6: Commit the orchestrator milestone**

```bash
git add src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java \
        src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/main/java/com/agent/editor/agent/v2/core/context/SupervisorContext.java \
        src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java
git commit -m "refactor: route orchestrators through document tool policy"
```

### Task 4: Add red tests for prompt branches that depend on visible tools

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`

**Step 1: Add failing React and writer prompt tests**

Cover:

- full-mode tool specs produce prompt text that allows whole-document snapshot workflow
- incremental-mode tool specs produce prompt text that emphasizes structure-first targeted reads and patches

**Step 2: Add failing reviewer and critic prompt tests**

Cover:

- reviewer prompt references `getDocumentSnapshot` only when that tool is visible
- reviewer prompt references `readDocumentNode` when incremental mode is visible
- reflexion critic analysis prompt does not claim unavailable document tools

**Step 3: Run focused tests to verify they fail**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,ReflexionCriticContextFactoryTest test
```

Expected: FAIL because prompts still hard-code one mode.

### Task 5: Implement prompt branching and verify full regression set

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java`

**Step 1: Implement prompt selection helpers**

Prefer small helper methods such as:

- `supportsIncrementalRead(context)`
- `supportsWholeDocumentSnapshot(context)`
- `supportsIncrementalPatch(context)`

Drive prompt sections from `context.getToolSpecifications()` instead of static assumptions.

**Step 2: Keep prompt guidance aligned with runtime enforcement**

Rules:

- do not mention `patchDocumentNode` if it is not visible
- do not mention `getDocumentSnapshot` if it is not visible
- keep existing structural JSON guidance for incremental flows

**Step 3: Re-run the focused prompt tests**

Use the same command from Task 4 Step 3.

Expected: PASS.

**Step 4: Run a broader regression suite**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentToolAccessPolicyTest,AgentV2ConfigurationSplitTest,SingleAgentOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest,ReactAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,ReflexionCriticContextFactoryTest test
```

Expected: PASS.

**Step 5: Commit the prompt and regression milestone**

```bash
git add src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java
git commit -m "refactor: align prompts with document tool mode"
```

### Task 6: Final verification and cleanup

**Files:**
- Review only existing touched files from Tasks 1-5

**Step 1: Run formatting or import cleanup if needed**

If Maven or IDE inspection reports unused imports or formatting drift, fix them before the final test run.

**Step 2: Run the targeted final suite again**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentToolModePropertiesTest,DocumentToolAccessPolicyTest,AgentV2ConfigurationSplitTest,SingleAgentOrchestratorTest,ReflexionOrchestratorTest,SupervisorOrchestratorTest,ReactAgentContextFactoryTest,GroundedWriterAgentContextFactoryTest,EvidenceReviewerAgentContextFactoryTest,ReflexionCriticContextFactoryTest test
```

Expected: PASS with no newly introduced failures in the touched coverage area.

**Step 3: Inspect the final diff**

Run:

```bash
git status --short
git diff --stat
```

Confirm that only the intended runtime, configuration, prompt, and test files changed.

**Step 4: Commit the final integrated change**

```bash
git add src/main/java/com/agent/editor/config/DocumentToolModeProperties.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicy.java \
        src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java \
        src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java \
        src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/main/java/com/agent/editor/agent/v2/core/context/SupervisorContext.java \
        src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java \
        src/main/resources/application.yml \
        src/test/java/com/agent/editor/config/DocumentToolModePropertiesTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/DocumentToolAccessPolicyTest.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java \
        src/test/java/com/agent/editor/agent/v2/task/SingleAgentOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactoryTest.java
git commit -m "feat: switch document tools by content size"
```

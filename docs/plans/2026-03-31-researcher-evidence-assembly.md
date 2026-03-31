# Researcher Evidence Assembly Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `ResearcherAgent` parse only model-generated summary fields and assemble the final `EvidencePackage` from the last real `retrieveKnowledge` tool interaction.

**Architecture:** Introduce a model-facing `ResearcherSummary` class so the model no longer generates deterministic retrieval fields. Keep `EvidencePackage` as the runtime-facing result, and build its `queries` and `chunks` from the last executed `retrieveKnowledge` argument/result in transcript memory. Update the researcher prompt to describe rewrite/split retrieval behavior and the new completion contract.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Jackson

---

### Task 1: Lock Down the New Researcher Completion Contract

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherSummary.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java`

**Step 1: Write the failing tests**

Add a test in `ResearcherAgentTest` asserting that when the model returns only:

```json
{
  "evidenceSummary":"supports supervisor",
  "limitations":"no metrics",
  "uncoveredPoints":["benchmark data"]
}
```

and transcript memory contains a last `retrieveKnowledge` tool result, the final `EvidencePackage` has:

- `queries == List.of("<last query>")`
- `chunks` parsed from that tool result
- summary fields copied from model output

Add a contract test in `EvidenceContractsTest` for deserializing `ResearcherSummary`.

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest#shouldAssembleEvidencePackageFromLastRetrieveKnowledgeResult,EvidenceContractsTest test`

Expected: FAIL because `ResearcherAgent` still expects the model to return a full `EvidencePackage`.

**Step 3: Write minimal implementation**

Create `ResearcherSummary` with:

```java
private String evidenceSummary;
private String limitations;
private List<String> uncoveredPoints;
```

Update `ResearcherAgent` completion parsing so it:

- parses `ResearcherSummary`
- finds the last executed `retrieveKnowledge` record in transcript memory
- extracts the query from tool arguments
- extracts chunks from tool result JSON
- assembles `EvidencePackage`

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest#shouldAssembleEvidencePackageFromLastRetrieveKnowledgeResult,EvidenceContractsTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherSummary.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java
git commit -m "feat: assemble researcher evidence from tool results"
```

### Task 2: Update Prompt and Context Tests

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Test: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`

**Step 1: Write the failing test**

Extend `ResearcherAgentContextFactoryTest` to assert the system prompt now mentions:

- query rewrite or rewritten retrieval
- splitting into multiple retrieval queries/tool calls
- final completion must match `ResearcherSummary`

Update any `ResearcherAgentTest` assertions that still expect the old `EvidencePackage`-shaped completion prompt.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentContextFactoryTest,ResearcherAgentTest#shouldExposeRetrieveKnowledgeOnly test`

Expected: FAIL because the old prompt text does not yet mention the new behavior/contract.

**Step 3: Write minimal implementation**

Update `systemPrompt()` so it explicitly says:

- first retrieval is already handled by runtime using the original instruction
- later the model may retry with rewritten queries or emit multiple `retrieveKnowledge` tool calls
- do not edit the document
- final JSON must match `ResearcherSummary`

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentContextFactoryTest,ResearcherAgentTest#shouldExposeRetrieveKnowledgeOnly test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java
git commit -m "refactor: clarify researcher summary prompt"
```

### Task 3: Focused Regression Verification

**Files:**
- Verify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Verify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java`
- Verify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherSummary.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java`
- Verify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java`

**Step 1: Run focused regression tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ResearcherAgentTest,ResearcherAgentContextFactoryTest,EvidenceContractsTest test`

Expected: PASS

**Step 2: Inspect diff**

Run: `git diff -- src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherSummary.java src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java`

Expected: Diff only shows the summary/evidence split, last retrieval assembly logic, and prompt/test updates.

**Step 3: Commit**

```bash
git add src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherSummary.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceContractsTest.java \
        docs/plans/2026-03-31-researcher-evidence-assembly.md
git commit -m "feat: split researcher summary from evidence package"
```

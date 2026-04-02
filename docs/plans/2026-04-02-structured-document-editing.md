# Structured Document Editing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a structure-first Markdown editing path that lets agents read chapters on demand, degrade oversized leaf chapters into runtime-only blocks, and patch incrementally without putting the whole document into the model context.

**Architecture:** Keep the server-side runtime state holding the full current document content so diff generation and final candidate reconstruction stay simple, but stop exposing that full content to the model by default. Add a `StructuredDocumentService` that derives chapter snapshots and oversized-leaf blocks from the current Markdown body, then add `readDocumentNode` and `patchDocumentNode` tools that operate against this service and return a rebuilt full-document candidate through the existing `ToolResult.updatedContent` channel.

**Tech Stack:** Java 17, Spring Boot, LangChain4j, flexmark-java, Lombok, JUnit 5, Maven

---

### Task 1: Add red tests for structure snapshots, oversized-leaf blocks, and baseline validation

**Files:**
- Create: `src/test/java/com/agent/editor/service/StructuredDocumentServiceTest.java`
- Modify: `src/test/java/com/agent/editor/service/DocumentServiceTest.java`

**Step 1: Write the failing structure snapshot tests**

Add tests that verify:

- Markdown headings become a stable tree of chapter nodes
- each node exposes `nodeId`, `path`, `leaf`, and `overflow`
- a long leaf chapter is marked as overflow while a small chapter is not

Suggested test shape:

```java
@Test
void shouldBuildStructureSnapshotWithStableHeadingPaths() {
    StructuredDocumentService service = new StructuredDocumentService(new MarkdownSectionTreeBuilder());

    DocumentStructureSnapshot snapshot = service.buildSnapshot("doc-1", "Title", """
            # Intro

            alpha

            ## Details

            beta
            """);

    assertEquals("doc-1", snapshot.getDocumentId());
    assertEquals(1, snapshot.getNodes().size());
    assertEquals("Intro", snapshot.getNodes().get(0).getHeadingText());
}
```

**Step 2: Write the failing oversized-leaf tests**

Add tests that verify:

- an oversized leaf returns paragraph-oriented blocks
- block metadata includes stable `blockId`, offsets, and `hash`
- reading a block returns only that block body rather than the whole leaf body

**Step 3: Write the failing patch validation tests**

Add tests that verify:

- `replace_node` rewrites a normal chapter and rebuilds the full Markdown content
- `replace_block` rewrites only one oversized-leaf block and rebuilds the full Markdown content
- stale `baseHash` triggers a baseline validation failure instead of silently writing stale content

**Step 4: Add a guardrail test for `DocumentService` ownership**

Keep `DocumentService` focused on persisted documents and diff history by asserting the new structured editing behavior lives in a dedicated service instead of being folded into `DocumentService`.

**Step 5: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredDocumentServiceTest,DocumentServiceTest test`

Expected: FAIL because the structured document service and state models do not exist yet.

### Task 2: Implement the structured document state model and service

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/DocumentStructureSnapshot.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/DocumentStructureNode.java`
- Create: `src/main/java/com/agent/editor/agent/v2/core/state/LeafBlockSnapshot.java`
- Create: `src/main/java/com/agent/editor/service/StructuredDocumentService.java`
- Modify: `src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionNode.java`
- Modify: `src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionDocument.java`
- Modify: `src/test/java/com/agent/editor/service/StructuredDocumentServiceTest.java`

**Step 1: Add immutable-ish state beans**

Create Lombok bean classes for:

- `DocumentStructureSnapshot`
- `DocumentStructureNode`
- `LeafBlockSnapshot`

Include fields from the approved design:

- document id, version, title, total estimated tokens
- node id, heading text, heading line, path, char length, estimated tokens, `leaf`, `overflow`
- block id, ordinal, offsets, estimated tokens, summary, hash

**Step 2: Extend Markdown section helpers only where needed**

Add minimal helper behavior so the service can:

- walk nodes with stable heading paths
- reconstruct chapter-local text and child boundaries
- find the source range for leaf nodes without re-parsing ad hoc strings in each tool

Keep these helpers reviewable and add concise Chinese comments only around boundary-sensitive logic.

**Step 3: Implement `StructuredDocumentService`**

Add methods such as:

- `buildSnapshot(String documentId, String title, String content)`
- `readNode(String documentId, String title, String content, String nodeId, String mode, String blockId)`
- `applyPatch(String documentId, String title, String content, PatchRequest request)`

Implementation rules:

- use `MarkdownSectionTreeBuilder` as the source of truth
- generate chapter node ids from heading path plus source order
- degrade only oversized leaf nodes into blocks
- prefer paragraph boundaries for block splits
- rebuild the full Markdown candidate after every successful patch

**Step 4: Re-run the focused service tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredDocumentServiceTest,DocumentServiceTest test`

Expected: PASS

**Step 5: Commit the service milestone**

Run:

```bash
git add src/main/java/com/agent/editor/agent/v2/core/state/DocumentStructureSnapshot.java \
        src/main/java/com/agent/editor/agent/v2/core/state/DocumentStructureNode.java \
        src/main/java/com/agent/editor/agent/v2/core/state/LeafBlockSnapshot.java \
        src/main/java/com/agent/editor/service/StructuredDocumentService.java \
        src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionNode.java \
        src/main/java/com/agent/editor/utils/rag/markdown/MarkdownSectionDocument.java \
        src/test/java/com/agent/editor/service/StructuredDocumentServiceTest.java \
        src/test/java/com/agent/editor/service/DocumentServiceTest.java
git commit -m "feat: add structured document service"
```

### Task 3: Add red tests for `readDocumentNode` and `patchDocumentNode`

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/ToolRegistryTest.java`
- Modify: `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java`

**Step 1: Write the failing read-tool tests**

Cover:

- `structure` mode returns node metadata without full body text
- `content` mode returns chapter-local content for a normal node
- oversized leaf `content` without `blockId` returns an instruction to read blocks instead of the whole leaf body
- oversized leaf `blocks` mode returns block metadata

**Step 2: Write the failing patch-tool tests**

Cover:

- `replace_node` returns an updated full candidate in `ToolResult.updatedContent`
- `replace_block` updates only the targeted leaf block
- stale baseline returns a failure message and no updated content

Suggested assertion shape:

```java
ToolResult result = tool.execute(
        new ToolInvocation("patchDocumentNode", json),
        structuredContext()
);

assertTrue(result.getMessage().contains("\"status\":\"ok\""));
assertTrue(result.getUpdatedContent().contains("rewritten block"));
```

**Step 3: Add failing registration tests**

Assert that:

- `DocumentToolNames` contains both new names
- `ToolRegistry` and Spring `ToolConfig` expose the new handlers
- configuration split tests still pass when the registry includes the new structured tools

**Step 4: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReadDocumentNodeToolTest,PatchDocumentNodeToolTest,ToolRegistryTest,AgentV2ConfigurationSplitTest test`

Expected: FAIL because the new tool handlers and names do not exist yet.

### Task 4: Implement the new document tools and register them

**Files:**
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeArguments.java`
- Create: `src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeArguments.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolNames.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java`

**Step 1: Add the new tool name constants**

Add:

- `READ_DOCUMENT_NODE`
- `PATCH_DOCUMENT_NODE`

Keep existing names for compatibility.

**Step 2: Implement the argument beans**

Use Lombok beans for:

- `ReadDocumentNodeArguments`
- `PatchDocumentNodeArguments`

Required fields:

- read: `nodeId`, `mode`, optional `blockId`, optional `includeChildren`
- patch: `documentVersion`, `nodeId`, optional `blockId`, `baseHash`, `operation`, `content`, optional `reason`

**Step 3: Implement the tool handlers**

Inject `StructuredDocumentService` and serialize tool responses as JSON strings in `ToolResult.message`.

Behavior:

- `readDocumentNode` delegates to the structured service and never mutates `updatedContent`
- `patchDocumentNode` delegates to the structured service and writes the rebuilt full document into `updatedContent` on success

**Step 4: Register the handlers in `ToolConfig`**

Keep legacy whole-document tools registered; add the new structured tools alongside them.

**Step 5: Re-run the focused tool tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReadDocumentNodeToolTest,PatchDocumentNodeToolTest,ToolRegistryTest,AgentV2ConfigurationSplitTest test`

Expected: PASS

**Step 6: Commit the tool milestone**

Run:

```bash
git add src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeTool.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeArguments.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeArguments.java \
        src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolNames.java \
        src/main/java/com/agent/editor/config/ToolConfig.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/ReadDocumentNodeToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/document/PatchDocumentNodeToolTest.java \
        src/test/java/com/agent/editor/agent/v2/tool/ToolRegistryTest.java \
        src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java
git commit -m "feat: add structured document tools"
```

### Task 5: Add red tests for structure-first context assembly and runtime tool context

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/document/StructuredDocumentToolContextTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Add failing context-factory assertions**

Verify that:

- initial agent-visible document context uses a structure summary rather than the raw full body
- prompts tell the model to inspect structure first and use `readDocumentNode` / `patchDocumentNode`
- supervisor routing summaries mention structure availability rather than dumping the full draft

**Step 2: Add failing tool-context assertions**

Verify that the runtime provides enough metadata for the structured tools, for example:

- `documentId`
- `documentTitle`
- server-side `currentContent`
- task-visible document version or snapshot metadata

**Step 3: Add a failing runtime loop test**

Add a tool loop test proving:

- one tool can patch a node
- a later tool call in the same loop sees the updated full document content server-side
- the model still receives only tool-result JSON rather than the entire document body in prompt assembly

**Step 4: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,SupervisorContextFactoryTest,GroundedWriterAgentContextFactoryTest,StructuredDocumentToolContextTest,ToolLoopExecutionRuntimeTest test`

Expected: FAIL because request/context/runtime classes still assume full-document prompt exposure.

### Task 6: Implement structure-first context assembly and runtime wiring

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/state/DocumentSnapshot.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java`

**Step 1: Extend `DocumentSnapshot` minimally**

Add optional fields for:

- `documentVersion`
- `DocumentStructureSnapshot structureSnapshot`

Keep `content` so the server runtime can still rebuild full candidates and diff output without a second persistence layer in v1.

**Step 2: Expand `ToolContext` and runtime construction**

Pass enough server-side metadata into each tool execution:

- task id
- document id
- document title
- current full content
- current document version
- optional structure snapshot

Do this in `ToolLoopExecutionRuntime.executeTools(...)`.

**Step 3: Change initial context assembly**

When building initial contexts:

- derive a structure snapshot from `request.getDocument()`
- keep full content out of model-visible prompt text
- preserve server-side full content in runtime state for tool execution and final candidate reconstruction

**Step 4: Update prompts and worker tool lists**

Prompts should explicitly say:

- inspect structure before reading content
- use `readDocumentNode` for targeted reads
- use `patchDocumentNode` for writes
- treat `editDocument` and `getDocumentSnapshot` as compatibility fallbacks for small documents only

Worker lists in `SupervisorAgentConfig` should expose the new tools where writing or local review is needed.

**Step 5: Re-run the focused context/runtime tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,SupervisorContextFactoryTest,GroundedWriterAgentContextFactoryTest,StructuredDocumentToolContextTest,ToolLoopExecutionRuntimeTest test`

Expected: PASS

**Step 6: Commit the runtime milestone**

Run:

```bash
git add src/main/java/com/agent/editor/agent/v2/core/state/DocumentSnapshot.java \
        src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRequest.java \
        src/main/java/com/agent/editor/agent/v2/tool/ToolContext.java \
        src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java \
        src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactory.java \
        src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentContextFactory.java \
        src/main/java/com/agent/editor/config/SupervisorAgentConfig.java \
        src/test/java/com/agent/editor/agent/v2/react/ReactAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentContextFactoryTest.java \
        src/test/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntimeTest.java
git commit -m "feat: wire structure-first document context"
```

### Task 7: Add orchestration and regression coverage for normal-node and oversized-leaf flows

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentTest.java`
- Modify: `src/test/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestratorTest.java`

**Step 1: Add failing prompt and allowed-tool assertions**

Verify:

- React and grounded-writer prompts instruct structure-first reads
- writer and reviewer worker definitions include the new tools
- legacy whole-document tools remain present only as fallback-compatible options

**Step 2: Add failing orchestration behavior assertions**

Cover:

- a normal-size chapter update can complete through node-level read and patch
- an oversized leaf chapter requires `blocks` discovery before patching
- final orchestrator output still returns a full candidate document string suitable for diff review

**Step 3: Run focused tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,SupervisorOrchestratorTest test`

Expected: FAIL until prompts, tool lists, and orchestration expectations are updated.

**Step 4: Implement the minimal changes needed to satisfy the regression suite**

Prefer test fixture updates and prompt/tool-list wiring over deeper orchestrator changes unless the tests prove a real runtime bug.

**Step 5: Re-run the focused orchestration tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,SupervisorOrchestratorTest test`

Expected: PASS

### Task 8: Run end-to-end verification and commit the completed feature

**Files:**
- Modify: all files touched in Tasks 1-7

**Step 1: Run the targeted long-document suite**

Run:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=StructuredDocumentServiceTest,ReadDocumentNodeToolTest,PatchDocumentNodeToolTest,ToolRegistryTest,AgentV2ConfigurationSplitTest,ReactAgentContextFactoryTest,PlanningAgentContextFactoryTest,SupervisorContextFactoryTest,GroundedWriterAgentContextFactoryTest,ToolLoopExecutionRuntimeTest,ReactAgentTest,GroundedWriterAgentTest,EvidenceReviewerAgentTest,SupervisorOrchestratorTest test`

Expected: PASS

**Step 2: Run the full test suite**

Run:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`

Expected: PASS

**Step 3: Review the resulting diff**

Run:

`git status --short`

Expected: only the intended structured-document files remain modified.

**Step 4: Commit the finished implementation**

Run:

```bash
git add src/main/java src/test/java docs/plans/2026-04-02-structured-document-editing-design.md docs/plans/2026-04-02-structured-document-editing.md
git commit -m "feat: add structure-first document editing"
```

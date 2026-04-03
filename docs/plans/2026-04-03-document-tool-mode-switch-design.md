# Document Tool Mode Switch Design

## Goal

Prevent model confusion between whole-document tools and structured incremental tools by selecting exactly one document toolset per task. Small documents should keep the existing whole-document workflow, while long documents should expose only incremental read and patch tools.

## Scope

In scope:

- add a shared policy that classifies the current document as full-mode or incremental-mode using `estimatedTokens`
- apply the policy to every `agent/v2` path that reads or writes document content through allowed tool lists
- update prompts so they describe the currently visible tool mode instead of hard-coding one editing workflow
- add configuration and tests that lock the switching behavior down

Out of scope:

- changing the legacy `agent/v1` tool layer
- removing legacy whole-document tools from the registry
- changing frontend diff confirmation or persistence flow
- adding a hybrid fallback where long-document tasks can still call whole-document reads

## Current Problem

The repository now contains two valid document tool families:

- whole-document tools: `editDocument`, `appendToDocument`, `getDocumentSnapshot`
- incremental tools: `readDocumentNode`, `patchDocumentNode`

They are registered together in the shared `ToolRegistry`. Some prompts already recommend the incremental tools, but several orchestrators and worker definitions still expose both families at the same time. This creates two failure modes:

- writer agents may call whole-document tools on long documents and waste context
- reviewer or critic-style subagents may call `getDocumentSnapshot` on long documents and overflow context even when they never patch content

The root problem is not tool implementation. It is that tool visibility is still mostly static and agent-specific instead of being derived from document size.

## Recommended Approach

Introduce a shared policy component that determines document access mode per task, then use that policy everywhere an execution request or worker dispatch chooses allowed tools.

Why this approach:

- it removes ambiguity at the runtime boundary instead of relying on prompt discipline
- it centralizes the long-document threshold so React, Reflexion, Supervisor, and future subagents follow one rule
- it preserves backward compatibility for small documents without rewriting the existing whole-document tools
- it lets reviewer-style agents avoid full-document reads on long content even when they do not edit

## Key Decisions

- Use `StructuredDocumentService` to compute `estimatedTokens` for the current document.
- Add a configurable long-document token threshold under `agent.document-tool-mode`.
- Expose only one document tool family per task.
- Distinguish tool access by role, not by concrete agent class.
- Keep `searchContent` available in both modes because it is already bounded to pattern-based inspection.
- For long documents, do not expose `getDocumentSnapshot` even as read-only fallback.

## Access Roles

### `WRITE`

Used by agents that can change document content:

- `ReactAgent` through `ReActAgentOrchestrator`
- reflexion actor through `ReflexionOrchestrator`
- supervisor writer worker through `SupervisorOrchestrator`

Allowed tools:

- full mode: `editDocument`, `appendToDocument`, `getDocumentSnapshot`, `searchContent`
- incremental mode: `readDocumentNode`, `patchDocumentNode`, `searchContent`

### `REVIEW`

Used by agents that inspect document content but should not mutate it:

- reflexion critic
- supervisor evidence reviewer worker

Allowed tools:

- full mode: `getDocumentSnapshot`, `searchContent`, `analyzeDocument`
- incremental mode: `readDocumentNode`, `searchContent`, `analyzeDocument`

### `RESEARCH`

Used by agents that gather external evidence rather than reading document body:

- supervisor researcher worker

Allowed tools stay unchanged:

- `retrieveKnowledge`

## Runtime Components

### `DocumentToolModeProperties`

Add a configuration properties bean, for example:

- prefix: `agent.document-tool-mode`
- field: `longDocumentThresholdTokens`

This gives operations a single place to tune when a task stops using whole-document tools.

### `DocumentToolAccessPolicy`

Add a shared Spring bean that:

1. accepts a `DocumentSnapshot` or equivalent `(documentId, title, content)` input
2. asks `StructuredDocumentService` for a structure snapshot
3. reads `estimatedTokens`
4. resolves a `DocumentToolMode` enum such as `FULL` or `INCREMENTAL`
5. returns the allowed tool names for a given `DocumentToolAccessRole`

This component should be the only place that knows the threshold and the tool lists.

## Orchestrator Integration

### ReAct

`ReActAgentOrchestrator` currently creates `ExecutionRequest` without explicit allowed tools. Update it so every run resolves `WRITE` access through the new policy and passes the result into `ExecutionRequest`.

### Reflexion

`ReflexionOrchestrator` currently uses static `ACTOR_ALLOWED_TOOLS` and `CRITIC_ALLOWED_TOOLS`.

Replace them with dynamic resolution:

- actor uses `WRITE`
- critic uses `REVIEW`

This ensures long-document reflexion runs never expose whole-document reads or writes.

### Supervisor

`SupervisorOrchestrator` currently forwards `WorkerDefinition.allowedTools` directly into worker execution requests.

Change it so worker dispatch resolves tools from the shared policy:

- writer worker uses `WRITE`
- reviewer worker uses `REVIEW`
- researcher worker keeps its static retrieval-only tool list

The `WorkerRegistry` should keep worker identity, capability tags, and descriptions, but it should stop being the source of truth for long-document document-tool switching.

## Prompt Behavior

Prompt text should reflect the visible tool mode instead of assuming one mode forever.

Affected context factories:

- `ReactAgentContextFactory`
- `GroundedWriterAgentContextFactory`
- `EvidenceReviewerAgentContextFactory`
- `ReflexionActorContextFactory` through inherited React prompt behavior
- `ReflexionCriticContextFactory`

Rules:

- if `readDocumentNode` is visible, prompt for structure-first targeted inspection
- if `getDocumentSnapshot` is visible, prompt for whole-document snapshot workflow
- never instruct the model to use tools that are not present in `context.getToolSpecifications()`

This keeps prompt guidance aligned with runtime enforcement.

## Testing Strategy

### Policy Tests

Add focused tests for the shared policy:

- below threshold returns full mode
- above threshold returns incremental mode
- `WRITE` returns write-capable tool family
- `REVIEW` returns review-capable tool family
- `RESEARCH` remains unchanged

### Orchestrator Tests

Update orchestrator tests so they assert resolved allowed tools instead of old static lists:

- `SingleAgentOrchestratorTest`
- `ReflexionOrchestratorTest`
- `SupervisorOrchestratorTest`

Cover both small and large document cases where practical.

### Context Factory Tests

Update prompt tests so they assert conditional wording based on visible tool specs rather than hard-coded assumptions. This is especially important for:

- `ReactAgentContextFactoryTest`
- `GroundedWriterAgentContextFactoryTest`
- reviewer and reflexion critic context factory tests

### Configuration Tests

Extend configuration wiring tests to cover:

- property binding for `agent.document-tool-mode.long-document-threshold-tokens`
- presence of the new policy bean
- continued registration of both tool families in the registry

## Risks And Mitigations

- Risk: prompt text drifts from actual allowed tools.
  Mitigation: build prompt branches from `context.getToolSpecifications()` and cover both branches in tests.

- Risk: a new worker is added later and bypasses the policy.
  Mitigation: keep the policy in orchestrator dispatch code and use role-based resolution instead of agent-name-specific conditionals.

- Risk: reviewer flows still inject the full current content directly into memory.
  Mitigation: when touching reviewer and critic paths, verify that long-document review prompts and memory assembly do not reintroduce full content outside the tool system.

## Success Criteria

- Every `agent/v2` execution path that reads or writes document content resolves document tool access through one shared policy.
- Small documents still support the legacy whole-document workflow.
- Long documents expose only incremental document access tools.
- Review-style agents no longer have access to `getDocumentSnapshot` on long documents.
- Existing registry-level compatibility remains intact because old tools stay registered, but they are hidden when not appropriate for the current task.

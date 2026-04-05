# Execution Tool Access Policy Design

## Goal

Replace the current orchestrator-side tool-list assembly with a single policy that returns the final tool whitelist for each execution path. This removes the awkward `MainAgentMemoryToolAccess.append(...)` composition from orchestrators and gives the codebase one obvious place to extend tool access when new tool domains are added.

## Scope

In scope:

- introduce a top-level policy for final execution tool visibility
- rename the memory helper into a policy-shaped component
- move main-write tool composition out of orchestrators
- keep current document and memory tool visibility behavior unchanged
- add focused tests for the new composition boundary

Out of scope:

- redesigning worker capability routing
- changing concrete tool names or tool implementations
- introducing a plugin registry or automatic classpath discovery
- changing supervisor worker/reviewer access rules

## Current Problem

Today the write-capable orchestrators build tool access in two steps:

1. `DocumentToolAccessPolicy` returns the document-tool whitelist
2. `MainAgentMemoryToolAccess.append(...)` mutates that list with memory tools

That has two issues:

- the composition responsibility lives in orchestrators instead of a dedicated policy
- the helper name describes an implementation detail rather than the actual outcome

As more tool families are added, each orchestrator would need to keep knowing how to merge more access helpers, making the construction site noisier and harder to evolve.

## Recommended Approach

Introduce `ExecutionToolAccessPolicy` as the single entry point for final tool access decisions during execution.

Responsibilities:

- `DocumentToolAccessPolicy` continues to own document-tool selection by document mode and access role
- `MemoryToolAccessPolicy` owns which execution roles may see memory tools
- `ExecutionToolAccessPolicy` combines domain-specific policies into the final ordered `List<String>` exposed to the runtime

This keeps domain rules local while moving composition to a single, explicit boundary.

## Naming

Approved naming:

- `ExecutionToolAccessPolicy`
- `ExecutionToolAccessRole`
- `MemoryToolAccessPolicy`

Why this naming:

- `ExecutionToolAccessPolicy` is aligned with `ExecutionRequest` semantics
- it communicates that this is the final execution-time whitelist, not a helper or utility
- it leaves room for multiple tool domains without encoding memory-specific or main-agent-specific behavior in the class name

## Role Model

Add a small execution-level role enum that reflects final composition semantics rather than document-only semantics.

Initial roles:

- `MAIN_WRITE`
- `REVIEW`
- `RESEARCH`

Behavior:

- `MAIN_WRITE` = document write tools + memory tools
- `REVIEW` = document review tools only
- `RESEARCH` = research retrieval tools only

This keeps existing behavior unchanged while making the role meaning obvious at the composition layer.

## Composition Rules

### `ExecutionToolAccessPolicy`

Inputs:

- `DocumentSnapshot` or pre-resolved `DocumentToolMode`
- `ExecutionToolAccessRole`

Output:

- final immutable ordered tool list

Rules:

- ask `DocumentToolAccessPolicy` for the document portion
- ask `MemoryToolAccessPolicy` for the memory portion
- append memory tools after document tools while deduplicating
- return `List.copyOf(...)`

### `MemoryToolAccessPolicy`

`MemoryToolAccessPolicy` replaces the current static helper.

Rules:

- `MAIN_WRITE` returns `searchMemory` and `upsertMemory`
- all other roles return an empty list

This keeps the current authority boundary:

- top-level execution actor can read/write long-term memory
- reviewers, researchers, and other non-main execution paths cannot

## Orchestrator Changes

The write orchestrators should stop composing tool domains directly.

Current pattern:

```java
MainAgentMemoryToolAccess.append(
    documentToolAccessPolicy.allowedTools(documentToolMode, DocumentToolAccessRole.WRITE)
)
```

Target pattern:

```java
executionToolAccessPolicy.allowedTools(currentDocument, ExecutionToolAccessRole.MAIN_WRITE)
```

Applies to:

- `ReActAgentOrchestrator`
- `PlanningThenExecutionOrchestrator`
- `ReflexionOrchestrator`

`SupervisorOrchestrator` can keep using `DocumentToolAccessPolicy` for worker routing because it does not currently need the execution-level memory composition.

## Spring Wiring

Add beans in `TaskOrchestratorConfig` for:

- `MemoryToolAccessPolicy`
- `ExecutionToolAccessPolicy`

Inject `ExecutionToolAccessPolicy` into the write orchestrators instead of only `DocumentToolAccessPolicy`.

Keep `DocumentToolAccessPolicy` as a standalone bean because it is still needed directly by supervisor and tests.

## Testing Strategy

Add focused policy-level tests instead of relying only on orchestrator tests.

### `MemoryToolAccessPolicyTest`

Cover:

- `MAIN_WRITE` exposes both memory tools
- non-main roles expose none

### `ExecutionToolAccessPolicyTest`

Cover:

- `MAIN_WRITE` combines document write tools and memory tools in order
- duplicate tools are not repeated
- `REVIEW` and `RESEARCH` do not include memory tools

### Existing orchestrator/config tests

Update only as needed:

- constructor wiring in orchestrator tests
- `AgentV2ConfigurationSplitTest` should assert the new beans exist

## Alternatives Considered

### 1. Rename `append(...)` only

Pros:

- smallest code change

Cons:

- orchestrators still own cross-domain composition
- extension pain remains

### 2. Full registry or contributor model

Pros:

- strongest long-term extensibility

Cons:

- unnecessary abstraction for two current domains
- higher indirection without immediate payoff

## Risks And Mitigations

- Risk: execution-level roles drift from document-level roles and create confusion.
  Mitigation: keep `ExecutionToolAccessRole` small and map explicitly to `DocumentToolAccessRole` inside the composition policy.

- Risk: tool ordering changes break assumptions in tests or prompts.
  Mitigation: preserve current order: document tools first, memory tools second.

- Risk: supervisor paths accidentally gain memory tools.
  Mitigation: limit `ExecutionToolAccessPolicy` adoption to main execution orchestrators and keep supervisor worker routing on `DocumentToolAccessPolicy`.

## Success Criteria

- orchestrators no longer call `MainAgentMemoryToolAccess.append(...)`
- a single `ExecutionToolAccessPolicy` decides final tool visibility for main execution paths
- adding a future tool domain only requires a new domain policy plus one composition change in `ExecutionToolAccessPolicy`
- existing runtime behavior stays the same for write, review, and research flows

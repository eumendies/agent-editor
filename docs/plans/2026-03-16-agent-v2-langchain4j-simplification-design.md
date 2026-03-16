# Agent V2 LangChain4j Simplification Design

**Date:** 2026-03-16

## Goal

Refactor the high-priority `agent.v2` protocol surfaces to use more of langchain4j's existing structured capabilities without changing the custom runtime and orchestration model.

This round covers:

- structured planning output
- stronger structured supervisor routing
- typed document tool arguments

This round does not cover:

- replacing the custom execution loop
- replacing the supervisor orchestrator
- converting ReAct to a fully library-managed agent runtime

## Why This Refactor

The current `agent.v2` package already uses langchain4j, but usage is uneven:

- `HybridSupervisorAgentDefinition` already uses `AiServices`
- document tools already use `ToolSpecification` and `JsonObjectSchema`
- `PlanningAgentDefinition` still uses manual prompting and manual line parsing
- document tools still parse raw JSON strings with `ObjectMapper.readTree(...)`

The result is unnecessary handwritten protocol code in exactly the places where langchain4j can already provide structured input/output affordances.

## Chosen Approach

Use langchain4j more aggressively at the model boundary, but keep the application's runtime boundary intact.

- `Planning` moves to `AiService + typed response`
- `Supervisor` keeps `AiService`, but tightens the typed response contract and removes prompt-level schema duplication
- document tools keep the existing `ToolHandler` abstraction, but decode arguments into typed value objects instead of reading raw JSON trees in each tool
- `DefaultExecutionRuntime`, `Decision`, `ToolCall`, and `SupervisorOrchestrator` remain custom

This keeps the highest-value simplifications while avoiding a risky rewrite of eventing, tracing, tool policy, and worker dispatch.

## Design

### 1. Planning

`PlanningAgentDefinition` will stop asking the model for numbered free-form text and stop parsing numbered lines manually.

Instead:

- add a `PlanningAiService`
- define a typed response object dedicated to planning output
- map that typed output into the existing `PlanResult` and `PlanStep`

Behavioral rules:

- blank or invalid planner output still falls back to the current single-step plan
- the public planning contract stays `PlanResult`
- orchestration remains unchanged

This removes prompt parsing methods whose only job is to recover structure from plain text.

### 2. Supervisor

`SupervisorRoutingAiService` already exists and should remain the only LLM-facing routing contract.

This refactor will:

- keep rule-based candidate filtering and fallback in `HybridSupervisorAgentDefinition`
- keep `SupervisorRoutingResponse` as the typed output contract
- remove redundant prompt prose that manually describes the JSON schema
- keep the current invalid-output fallback path

Behavioral rules:

- illegal worker ids still fall back to the first valid candidate
- model failure still falls back to rule-based routing
- no-progress termination remains rule-driven, not model-driven

The key change is simplification, not behavior expansion.

### 3. Tool Arguments

`EditDocumentTool` and `SearchContentTool` will stop pulling fields from raw `JsonNode`.

Instead:

- add typed argument objects such as `EditDocumentArguments` and `SearchContentArguments`
- centralize decoding from the raw tool argument JSON string into those types
- keep tool business logic focused on validation and document behavior

Behavioral rules:

- malformed JSON still fails fast with a clear `IllegalArgumentException`
- missing required fields still return the same user-visible tool result or failure shape as today
- `AnalyzeDocumentTool` remains simple and may keep its empty-parameter shape

This removes repeated JSON plumbing without forcing a runtime-wide tool protocol rewrite.

### 4. Runtime Boundary

The following classes stay custom and intentionally do not move to a langchain4j-managed runtime:

- `DefaultExecutionRuntime`
- `SupervisorOrchestrator`
- `Decision`
- `ToolCall`
- `ExecutionState`

Reason:

- they own the application-specific execution loop
- they enforce tool allow-lists
- they publish events and traces
- they control document state progression across iterations and workers

These are not accidental abstractions; they are the core runtime contract of the application.

## Data Flow

### Planning

`PlanningThenExecutionOrchestrator` -> `PlanningAgentDefinition` -> `PlanningAiService` -> typed planning response -> `PlanResult`

### Supervisor

`SupervisorOrchestrator` -> `HybridSupervisorAgentDefinition` -> rule candidate filtering -> `SupervisorRoutingAiService` -> `SupervisorRoutingResponse` -> `SupervisorDecision`

### Tools

`DefaultExecutionRuntime` -> raw `ToolCall.arguments()` -> typed argument decoder inside tool layer -> tool logic -> `ToolResult`

## Error Handling

- Planning service failures fall back to the existing single-step plan.
- Supervisor routing failures fall back to the existing rule-based assignment.
- Tool argument decoding failures surface as explicit `IllegalArgumentException`s.
- No runtime or orchestration fallback behavior is removed in this refactor.

## Testing Strategy

Add or update tests in the existing package-aligned test structure:

- `PlanningAgentDefinitionTest`
- `HybridSupervisorAgentDefinitionTest`
- `EditDocumentToolTest`
- `SearchContentToolTest`

Test focus:

- typed planning output maps correctly into `PlanResult`
- planner fallback still works when the model output is empty or invalid
- supervisor still completes or falls back correctly on invalid routing output
- typed tool argument decoding accepts valid JSON and rejects invalid JSON

## Risks

### 1. Over-coupling to generated structure

If the typed AI response contracts become too rigid, tests may become brittle across prompt changes. Keep response objects minimal and aligned with current behavior only.

### 2. Hidden behavior drift

If planning or supervisor fallback behavior changes while simplifying parsing, orchestration tests may still pass while semantics drift. Keep current fallback paths explicit and covered by tests.

### 3. Scope creep into runtime

It will be tempting to also simplify `ReactAgentDefinition` or the runtime loop in this round. Do not do that here.

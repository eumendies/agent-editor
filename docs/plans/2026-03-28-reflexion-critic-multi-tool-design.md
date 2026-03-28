# Reflexion Critic Multi-Tool Design

**Goal:** Let `ReflexionCritic` perform real multi-round tool calling before emitting a final structured critique.

## Problem

`ReflexionCritic` currently sends both tool specifications and a strict JSON response schema in the same request. In practice this causes some model/provider combinations to emit tool-call-like JSON as plain text rather than structured tool calls. As a result, `AiMessage.hasToolExecutionRequests()` stays `false` even when the text payload looks like a tool invocation.

## Chosen Approach

Use a two-phase contract inside `ReflexionCritic`, while still relying on the existing `ToolLoopExecutionRuntime` loop:

1. **Analysis phase**
   - Allow real tool calls.
   - Do not force strict JSON schema output.
   - The critic may either call another tool or directly return a critique if it already has enough evidence.

2. **Finalization phase**
   - Once tool results are present in the current critic memory, switch to final critique generation.
   - Request strict JSON matching `ReflexionCritique`.
   - Do not allow another round of tool calling in this phase.

This preserves the current orchestrator architecture: the critic remains a `ToolLoopAgent`, and the runtime continues to drive `decide -> tool -> decide` until `Complete`.

## Why This Approach

- Keeps tool loop behavior in runtime, not in `ReflexionOrchestrator`.
- Avoids introducing extra agent types or a second critic orchestrator branch.
- Matches the existing architecture used by other v2 tool-loop agents.
- Minimizes code changes to `ReflexionCritic` and its tests.

## Behavior Rules

- If the critic memory contains no tool execution results for the current run, the critic operates in analysis mode.
- If the model returns structured tool calls, the critic returns `ToolLoopDecision.ToolCalls`.
- If the model returns a direct critique text payload, the critic parses it and returns `Complete`.
- If the critic memory already contains tool execution results, the critic operates in finalization mode and requests strict JSON critique output.
- The critic may keep making multiple tool calls across multiple runtime iterations until the model decides to stop.

## Test Impact

- Add a critic test for `ToolCalls -> tool result in memory -> Complete`.
- Keep existing critique parsing tests.
- Add an orchestrator-level test covering a critic that needs one or more tool rounds before producing `PASS` or `REVISE`.

# Remove Trace Calls Design

**Date:** 2026-03-27

**Goal:** Remove all current trace write calls from business execution paths while keeping the trace data model, store, query API, and controller intact.

## Scope

This change removes only active trace production from runtime code. It does not remove trace infrastructure.

Remove:
- `traceCollector.collect(...)` calls
- helper methods that only exist to build `TraceRecord`
- constructor parameters, fields, and imports that are only needed for trace emission

Keep:
- `TraceRecord`
- `TraceCollector`
- `TraceStore`
- `InMemoryTraceStore`
- `TraceController`
- trace-related DTOs and query/read paths

After the change, reading trace data still works, but new execution flows will no longer append trace records.

## Targeted Areas

The main write sites are concentrated in these classes:

- `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`
- `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`
- `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticDefinition.java`
- `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentDefinition.java`
- `src/main/java/com/agent/editor/agent/v2/supervisor/worker/GroundedWriterAgentDefinition.java`
- `src/main/java/com/agent/editor/agent/v2/supervisor/worker/EvidenceReviewerAgentDefinition.java`

There are also Spring configuration and tests that currently pass `TraceCollector` into these types and assert trace content exists.

## Design Decisions

### 1. Remove write-side dependencies completely

The affected orchestrators, runtime classes, and agent definitions will no longer depend on `TraceCollector`.

This means:
- constructor signatures shrink
- Spring wiring is simplified
- tests stop building fake trace collectors for these classes

### 2. Keep event publishing untouched

`EventPublisher` and websocket/event behavior remain unchanged. This task is only about trace writes, not all observability.

This is important because several tests and user-visible flows rely on execution events, not trace records.

### 3. Keep read-side trace infrastructure intact

The trace controller and store remain because the user explicitly asked to remove only trace-producing calls, not trace support itself.

This preserves compatibility for:
- existing endpoints
- trace storage unit tests
- future reintroduction of tracing if needed

### 4. Replace trace assertions with business assertions

Tests that currently assert trace stages or payloads will be rewritten to assert:
- task results
- final content
- event emission
- worker/tool selection behavior
- memory/state transitions

This keeps behavioral coverage while removing coupling to trace internals.

## Risks

### Wiring regressions

Removing `TraceCollector` from constructors changes bean creation signatures and test fixtures. This is a compile-time risk and should be handled early.

### Test expectation drift

Some tests currently use trace assertions as proxies for orchestration correctness. Those assertions need to be replaced carefully so we do not accidentally reduce coverage.

### Hidden trace writes

There may be less obvious write sites in agent definitions or helper paths. The implementation should use `rg` across `src/main/java` to ensure all `traceCollector.collect(...)` calls are removed.

## Verification

The change is complete when all of the following are true:

- `rg "traceCollector.collect|new TraceRecord\\(" src/main/java` shows no remaining business-path trace writes outside trace infrastructure itself
- the application compiles
- full test suite passes
- trace read-side code still compiles and its own tests continue to pass

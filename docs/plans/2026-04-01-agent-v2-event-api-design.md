# Agent V2 Event API Design

**Date:** 2026-04-01

**Goal:** Keep existing `/api/v1/agent/**` and `/ws/agent` compatibility paths intact, add native v2 agent event APIs, move the demo page to the new APIs, and make `LegacyEventAdapter` removable.

## Scope

This change only targets the agent execution protocol layer.

Add:
- native v2 HTTP endpoints for agent task execution and event queries
- native v2 WebSocket event stream for task updates
- frontend changes so the demo page consumes native `ExecutionEvent` payloads

Keep:
- existing `/api/v1/agent/**` endpoints
- existing `/ws/agent`
- existing document, diff, knowledge, and trace APIs

Remove after migration:
- `LegacyEventAdapter`
- v2 runtime dependencies on legacy `AgentStep` / legacy websocket step payloads

## Current Problem

`agent.v2` already uses `ExecutionEvent` as its internal runtime event model, but the public-facing UI path still projects those events back into the legacy protocol.

Today the compatibility path is split across:

- `WebSocketEventPublisher`, which persists `ExecutionEvent` and also converts it into legacy `WebSocketMessage`
- `TaskQueryService#getTaskSteps`, which converts stored `ExecutionEvent` into legacy `AgentStep`
- `index.html`, which consumes `STEP` / `COMPLETED` / `ERROR` websocket messages rather than native v2 events

This means `LegacyEventAdapter` is not a v1 runtime dependency. It is a protocol translation layer kept alive by the frontend.

## Design Decisions

### 1. Make `ExecutionEvent` the only v2 event contract

The new v2 agent APIs should expose native `ExecutionEvent` objects directly instead of translating them into:

- `AgentStep`
- legacy `WebSocketMessage.step(...)`
- synthetic `COMPLETED` / `ERROR` websocket message types

This keeps one event model from orchestrator to UI and removes the need for parallel protocol semantics.

### 2. Add parallel v2 endpoints instead of mutating v1 contracts

The user explicitly wants old interfaces preserved while the frontend moves to new ones. The cleanest boundary is to add a separate v2 surface:

- `POST /api/v2/agent/execute`
- `GET /api/v2/agent/task/{taskId}`
- `GET /api/v2/agent/task/{taskId}/events`
- `GET /api/v2/agent/modes`
- `POST /api/v2/agent/connect` if session bootstrap is still needed for the page
- `/ws/agent/v2` for realtime native events

The existing v1 controller and websocket handler stay available without behavior changes.

### 3. Reuse application/orchestration services where behavior is identical

Task execution itself is already v2-backed. The new controller layer should reuse existing application services where possible instead of duplicating orchestration logic.

The key backend change is query shape:

- keep existing task execution service methods for starting and checking tasks
- add direct event query support for v2 consumers
- stop requiring `TaskQueryService` to synthesize `AgentStep` for the new path

This keeps the change focused on protocol migration rather than deeper runtime rewrites.

### 4. Split websocket payload models by protocol version

The current websocket infrastructure only sends `WebSocketMessage`. The v2 stream should instead send native event envelopes shaped around `ExecutionEvent`.

Recommended approach:

- keep the existing `WebSocketService` and `AgentWebSocketHandler` for v1
- add a dedicated v2 websocket message model only for connection bootstrap if needed
- add a separate v2 handler/service path that serializes `ExecutionEvent` payloads directly

This avoids contaminating the native v2 stream with legacy fields such as `stepType`.

### 5. Update the demo page to use only the new agent event protocol

The page should switch only its agent-related calls:

- execution request to `/api/v2/agent/execute`
- task polling to `/api/v2/agent/task/{taskId}` if used
- event replay to `/api/v2/agent/task/{taskId}/events`
- realtime updates to `/ws/agent/v2`

Document, diff, knowledge, and trace panels can remain on their current endpoints because they are outside the compatibility problem this task is solving.

The UI timeline should render directly from `ExecutionEvent.type` and `ExecutionEvent.message` instead of relying on:

- websocket `type = STEP|COMPLETED|ERROR`
- legacy `stepType`
- `AgentStep.action/result/error/...`

### 6. Remove `LegacyEventAdapter` only after the new page path is live

Deletion should happen in the same implementation sequence, but only after:

- the frontend no longer consumes legacy websocket step payloads
- backend v2 endpoints are in place
- tests are updated to assert native `ExecutionEvent` payloads

At that point:

- `WebSocketEventPublisher` should forward native events to the v2 websocket path
- `TaskQueryService` should expose stored events directly for v2
- any remaining legacy step projection should stay only behind v1 compatibility paths, or be deleted if unused

The intended final state is that `agent.v2` no longer depends on `LegacyEventAdapter` at all.

## Data Flow

### New v2 runtime flow

1. Frontend opens `/ws/agent/v2`
2. Frontend starts a task via `POST /api/v2/agent/execute`
3. Backend executes the existing v2 orchestrator
4. Each `ExecutionEvent` is:
   - appended to `TaskQueryService`
   - pushed to v2 websocket subscribers as native event payload
5. Frontend timeline renders directly from those events
6. On refresh or reconnect, frontend reloads `GET /api/v2/agent/task/{taskId}/events`

### Legacy v1 flow

The old v1 HTTP and websocket interfaces remain available during the migration window. They are compatibility-only paths and should not be used by the demo page after this change.

## Error Handling

- The v2 HTTP event query should return an empty list for unknown tasks only if that matches current query semantics; otherwise return `404` consistently with existing task status behavior.
- The v2 websocket connection should still send an initial connection acknowledgment so the page can capture `sessionId`.
- Frontend should treat task failure as an `ExecutionEvent` with failure type rather than depending on a distinct websocket `ERROR` message type.
- Reconnect logic should remain in place; after reconnect the page should fetch `/events` to backfill anything missed.

## Testing Strategy

Backend:
- controller tests for new `/api/v2/agent/**` endpoints
- `TaskQueryService` tests asserting raw `ExecutionEvent` query behavior
- websocket publisher tests asserting native event payload delivery
- removal/update of `LegacyEventAdapter` tests

Frontend:
- verify timeline rendering still distinguishes action / observation / completion / failure from native event types
- verify reconnect and replay behavior with `/events`

Regression:
- existing `/api/v1/agent/**` tests should keep passing
- existing document/diff/knowledge flows should remain unchanged

## Risks

### Mixed protocol leakage

If the v2 websocket path still serializes legacy `WebSocketMessage`, `LegacyEventAdapter` will remain structurally required. The implementation should keep the payload contract unambiguous.

### Hidden consumers of `/task/{taskId}/steps`

The current demo page does not appear to use `/steps`, but tests and older clients may. That endpoint should remain untouched unless we verify it is dead and explicitly choose to delete it later.

### Realtime + replay divergence

The same stored `ExecutionEvent` should drive both replay and realtime behavior. If realtime emits a wrapper shape different from `/events`, the frontend will end up with two render paths again.

## Verification

The migration is complete when all of the following are true:

- the demo page no longer references `/api/v1/agent/execute` or `/ws/agent`
- the demo page timeline consumes native `ExecutionEvent` data from both websocket and replay APIs
- `LegacyEventAdapter` has no remaining production references and can be deleted
- v1 agent compatibility endpoints still compile and their tests continue to pass

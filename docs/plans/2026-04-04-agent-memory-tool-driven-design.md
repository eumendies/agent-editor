# Agent Memory Tool-Driven Design

**Context**

The current `agent-memory` branch already has a first long-term-memory implementation, but it is optimized for candidate review:

- `USER_PROFILE` is auto-injected during task initialization
- `DOCUMENT_DECISION` can be loaded through `memory_search`
- post-task extraction produces pending candidates
- the user must explicitly confirm candidates before they are persisted

That design is structurally sound, but the product experience is too heavy for this project. The user has to review memory writes manually, while the runtime is also paying for a separate extraction path that sits outside the main dialogue loop.

The approved redesign is to simplify the system into a tool-driven memory model:

- `USER_PROFILE` can be edited directly in the UI
- `USER_PROFILE` can also be created or updated by the main agent
- `DOCUMENT_DECISION` is only written or corrected by the main agent during the main conversation
- there is no pending-candidate review flow
- there is no separate conflict-detection model
- there is no task-end background extraction flow

**Goals**

- Remove the explicit user-confirmation burden from long-term memory writes.
- Keep `USER_PROFILE` editable by the user while still allowing the agent to learn stable preferences.
- Keep `DOCUMENT_DECISION` grounded in the main conversation instead of a second post-task summarization step.
- Let the main agent correct stale memories when current user input conflicts with retrieved memory.
- Preserve the existing separation between short-term in-memory transcript memory and Milvus-backed long-term memory.

**Non-Goals**

- No short-term memory persistence
- No additional conflict-detection or merge-decision model
- No pending-memory storage, review queue, or confirmation API
- No worker-agent memory mutation
- No automatic task-end summarization job for memory writes
- No fake `userId` or `workspaceId` concepts

**Core Design**

The long-term memory system becomes a pair of explicit capabilities exposed to the main agent:

- `memory_search`
- `memory_upsert`

The agent stays responsible for deciding when memory matters. When the model needs history, it calls `memory_search`. If the retrieved memory conflicts with current user statements, or if the user clearly states a durable preference or document rule, the model calls `memory_upsert`.

This keeps retrieval, reasoning, and correction inside the main dialogue path instead of splitting them across separate background services.

**Memory Types**

The system still keeps only two memory types:

`USER_PROFILE`
- Global preference memory
- Loaded automatically at task initialization
- Can be created, replaced, or deleted either by UI operations or by the main agent

Examples:
- default to Chinese
- prefer proposal-first interaction
- keep edits conservative unless explicitly asked to rewrite heavily

`DOCUMENT_DECISION`
- Document-scoped historical decision memory
- Not injected automatically into every task
- Retrieved only through `memory_search`
- Can only be created, replaced, or deleted by the main agent

Examples:
- do not rewrite section 3 of this document
- keep the current title hierarchy
- for this document, avoid speculative claims without citations

**Runtime Flow**

The main runtime flow becomes:

1. Load short-term `ChatTranscriptMemory` as before.
2. Load persisted `USER_PROFILE` memories and inject them into the execution context.
3. Start the main agent with both `memory_search` and `memory_upsert`.
4. The agent calls `memory_search` when previous profile or document context may matter.
5. If the user clearly states a durable preference or a durable document rule, the agent may call `memory_upsert(CREATE)`.
6. If retrieved memory conflicts with the user's current statement, the agent may call `memory_upsert(REPLACE)` or `memory_upsert(DELETE)`.

There is no post-task extraction or confirmation step after the main agent finishes.

**Tool Responsibilities**

`memory_search`
- Returns confirmed long-term memory only
- Supports semantic retrieval for `DOCUMENT_DECISION`
- Must include `memoryId` in results so the model can target later updates
- Should return concise cards, not raw transcript or internal entities

`memory_upsert`
- Only available to the main executing agent
- Supports:
  - `CREATE`
  - `REPLACE`
  - `DELETE`
- Input fields:
  - `action`
  - `memoryType`
  - `memoryId` optional
  - `documentId` optional
  - `summary`

Behavior rules:
- `USER_PROFILE` must not require `documentId`
- `DOCUMENT_DECISION` must require `documentId` on create
- `REPLACE` and `DELETE` should target an existing `memoryId`
- The tool must reject invalid combinations instead of guessing

**Persistence Model**

Milvus continues to store only persisted, active long-term memories. There is no `pending` or `candidate` layer anymore.

The application layer owns replacement semantics:

- `CREATE`: insert a new memory row
- `DELETE`: remove the existing row
- `REPLACE`: load the old memory, delete it, then insert the new memory

This is preferred over relying on opaque Milvus update semantics. It keeps repository behavior explicit and matches the project goal of learning the memory workflow rather than hiding it behind database-specific mutation features.

**USER_PROFILE Management**

`USER_PROFILE` now has two write paths:

1. UI path
   - The user can list, create, update, and delete profile memories directly.
2. Agent path
   - The main agent can call `memory_upsert` when the user has clearly expressed a durable preference.

These two paths write to the same long-term-memory store. The system does not need a separate profile table.

**Document Decision Correction**

The system does not run a separate correction model.

Instead, correction happens naturally in the main conversation:

1. The agent retrieves relevant document memory with `memory_search`.
2. The user says something that explicitly negates or materially conflicts with that memory.
3. The model recognizes the conflict in-context.
4. The model calls `memory_upsert(REPLACE)` or `memory_upsert(DELETE)`.

This keeps cost down and places the correction decision in the same reasoning context where the conflict is actually visible.

**Required Codebase Changes**

The current candidate-review implementation should be simplified:

- delete `PendingLongTermMemoryItem`
- delete `PendingLongTermMemoryService`
- delete candidate confirmation DTOs
- remove pending-memory fields from task responses
- remove pending-memory endpoints from `LongTermMemoryController`
- stop calling `LongTermMemoryExtractor` from `TaskApplicationService`
- likely remove the extractor classes and configuration entirely if they are no longer used

At the same time, the long-term-memory repository and retrieval service should stay in place, with write operations expanded to support tool-driven mutation.

**API Surface**

The minimum HTTP API surface becomes:

- `GET /api/v2/memory/profiles`
- `POST /api/v2/memory/profiles`
- `PUT /api/v2/memory/profiles/{memoryId}`
- `DELETE /api/v2/memory/profiles/{memoryId}`

There is no HTTP API for pending candidates anymore.

`DOCUMENT_DECISION` writes stay tool-only in the first version.

**Review Notes**

- Do not keep the pending-review path "just in case". It directly conflicts with the new UX.
- Do not keep task-end automatic extraction in the main flow. It would create a second, competing write path.
- Do not give `memory_upsert` to worker agents.
- Do not silently infer missing `documentId` for document decisions.
- Do not hide replacement semantics inside Milvus-specific magic. Keep delete-plus-insert explicit in application code.

# Agent Memory Autonomous Design

**Context**

The current long-term memory branch introduced a confirmation-based memory flow:

- `USER_PROFILE` is loaded at task start
- `DOCUMENT_DECISION` can be retrieved with `memory_search`
- task completion extracts candidate memories
- candidates wait for explicit user confirmation before persistence

That model is technically workable, but the product experience is too heavy:

- users should not need to confirm memory writes one-by-one
- `USER_PROFILE` should be directly editable in the UI
- the main agent should be able to search, create, replace, and delete memories during normal conversation
- memory correction should happen inside the main reasoning chain instead of via a separate background judge

This redesign keeps the long-term memory domain, Milvus storage, and retrieval foundation, but changes the interaction model completely.

**Goals**

- Remove the pending-candidate confirmation loop from the product path.
- Let users directly manage `USER_PROFILE` through dedicated APIs/UI.
- Let the main execution agent use memory tools to write and correct both `USER_PROFILE` and `DOCUMENT_DECISION`.
- Keep `USER_PROFILE` automatically loaded at task initialization.
- Keep `DOCUMENT_DECISION` behind on-demand retrieval rather than eager prompt injection.
- Keep memory correction in the primary model loop instead of introducing another dedicated conflict-detection model.

**Non-Goals**

- No separate conflict-detection LLM pass.
- No background task-end auto-summary write for `DOCUMENT_DECISION`.
- No user confirmation queue for memory persistence.
- No scope model beyond current `USER_PROFILE` global memory and `DOCUMENT_DECISION` per-document memory.
- No multi-user or workspace identity system in this iteration.

**Core Product Model**

The new product behavior has two memory classes with different authoring flows.

`USER_PROFILE`
- Can be created, edited, and deleted directly by the user in the UI.
- Can also be created or corrected by the main agent with a tool call.
- Is always loaded before task execution and turned into prompt guidance.
- Represents durable user preferences and constraints.

`DOCUMENT_DECISION`
- Is not written automatically at task end anymore.
- Is only created or corrected when the main agent explicitly decides to call a memory-writing tool during normal conversation.
- Is retrieved only through `memory_search`.
- Represents durable document-specific decisions or constraints.

This means memory persistence is no longer a post-processing workflow. It becomes part of the agent tool layer.

**Tool Model**

Two tools remain in the system:

1. `memory_search`
2. `memory_upsert`

`memory_search`
- Available when the model needs to inspect prior memory.
- Returns compact memory cards including `memoryId`, `memoryType`, `documentId`, and `summary`.
- Lets the model compare user statements with historical memory before deciding whether correction is needed.

`memory_upsert`
- Available only to the main execution agent.
- Supports:
  - `CREATE`
  - `REPLACE`
  - `DELETE`
- Minimal input:
  - `action`
  - `memoryType`
  - `memoryId` optional
  - `documentId` optional
  - `summary`

Rules:
- `USER_PROFILE` does not need `documentId`
- `DOCUMENT_DECISION` requires `documentId`
- `REPLACE` and `DELETE` should normally target an existing `memoryId`

There is no `scopeKey` in this version. Current behavior is already expressed by:
- `USER_PROFILE` as global profile memory
- `DOCUMENT_DECISION` with `documentId`

**Why Correction Lives In The Main Chain**

The design intentionally avoids a separate conflict-detection model.

Expected agent flow:

1. User gives a new instruction or correction.
2. The model notices that historical memory may matter.
3. The model calls `memory_search`.
4. Search results are returned in-context.
5. If the model sees that current user input conflicts with stored memory, it calls `memory_upsert(REPLACE|DELETE)`.

This preserves a single reasoning chain:
- retrieve
- compare
- correct

Benefits:
- lower cost
- fewer background moving parts
- correction is based on the exact conversational context the model is already handling

This also gives the model an interpretable correction path instead of hiding correction in a separate subsystem.

**Persistence Semantics**

Memory updates should not rely on opaque Milvus upsert behavior alone.

Application semantics should be:

- `CREATE`
  - create a new memory row
- `REPLACE`
  - application loads the target memory
  - delete old row
  - insert replacement row
- `DELETE`
  - delete target row

So the repository layer should expose focused operations for:
- create
- delete
- list/load
- search

The application service should orchestrate `delete + insert` when performing replacements.

This is simpler to reason about than trying to encode semantic replacement into raw vector-store updates.

**User Profile Management APIs**

`USER_PROFILE` now needs its own explicit management API surface.

Minimum endpoints:
- list all profiles
- create profile
- update profile
- delete profile

These APIs are not task-scoped. They are direct memory-management APIs.

They coexist with agent tool-driven writes:
- user can edit profiles manually
- agent can also maintain them through `memory_upsert`

The UI can later render this as a mixed panel:
- structured common fields
- free-text custom profile rules

But the backend should keep the persistence model generic and summary-based.

**Changes To Existing Components**

`TaskApplicationService`
- Stop extracting pending long-term memory candidates at task end.
- Stop returning pending memory candidates in task responses.
- Keep loading confirmed `USER_PROFILE` before execution.

`LongTermMemoryExtractor`
- Can remain as reusable extraction infrastructure, but it should no longer be part of the main post-task persistence path for this design.
- If retained, it should become an optional helper rather than a required task-finalization step.

`PendingLongTermMemoryService`
- Becomes obsolete in the new product flow.

`LongTermMemoryController`
- Should be redesigned away from pending/confirm/discard.
- Replace it with:
  - profile management endpoints
  - possibly direct memory inspection/debugging endpoints

`MemorySearchTool`
- Must return `memoryId` so the model can target replacement/deletion.

**Guardrails**

To avoid memory corruption, tool descriptions and prompting should enforce:

- only write memory when the user has made a clear durable statement or correction
- do not store temporary task steps
- do not overwrite memory based on weak inference
- for `REPLACE`/`DELETE`, prefer explicit contradiction or explicit user correction

The system should bias toward not writing when the signal is weak.

**Resulting Runtime Behavior**

Final runtime model:

1. Load confirmed `USER_PROFILE` and inject prompt guidance.
2. Run the main execution agent.
3. Agent uses `memory_search` when relevant.
4. Agent uses `memory_upsert` to create or correct memory in the main chain.
5. User can separately manage `USER_PROFILE` through dedicated APIs/UI.

This gives a much cleaner user experience than the previous candidate-confirmation design, while keeping memory operations explicit and auditable through tool calls.

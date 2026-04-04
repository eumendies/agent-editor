# Agent Memory Design

**Context**

The v2 agent runtime already has short-term session memory in place through `SessionMemoryTaskOrchestrator`, `SessionMemoryStore`, and `ChatTranscriptMemory`. That memory is intentionally in-process only and uses transcript compression to stay within token limits. For this learning project, short-term memory should remain in-memory and should not introduce Redis or database persistence.

The new requirement is to add a long-term memory system that helps document-editing tasks continue across runs without forcing the user to repeat preferences or historical decisions. The approved scope is intentionally narrow:

- keep short-term memory in-memory only
- add long-term memory for `user_profile` and `task_decision`
- load `user_profile` automatically during task initialization
- let the model call a `memory_search` tool to load `task_decision` on demand
- store only confirmed long-term memories in Milvus
- keep unconfirmed candidates outside Milvus and discard them if the user does not confirm

**Goals**

- Preserve the current in-memory short-term memory flow with no new persistence dependency.
- Add a distinct long-term memory layer instead of extending transcript memory.
- Make `user_profile` behave like stable runtime guidance that is always loaded before execution.
- Make `task_decision` retrievable only when the model explicitly needs prior decisions.
- Keep the confirmation loop simple:
  - extractor produces candidate memories
  - candidates live in a temporary in-memory store
  - user confirmation promotes them into Milvus
  - non-confirmed candidates are ignored
- Reuse the existing Milvus and embedding infrastructure where it actually adds learning value.

**Non-Goals**

- No short-term memory persistence
- No multi-user or workspace scoping in the first version
- No candidate persistence in Milvus or a relational database
- No generalized memory graph or relationship engine
- No automatic forgetting, TTL, or complex merge policies
- No attempt to store raw transcript history as long-term memory

**Core Design Decision**

The system should treat short-term memory and long-term memory as different products:

- short-term memory answers "what happened in this running session?"
- long-term memory answers "what stable preference or prior decision should influence future work?"

This means long-term memory must not reuse `ChatTranscriptMemory` as a storage model. It needs its own structured item model, retrieval path, and confirmation flow.

**Long-Term Memory Types**

The first version supports only two memory types.

`USER_PROFILE`
- Stable user preference or operating preference
- Examples:
  - default to Chinese
  - prefer a conservative editing style
  - prefer proposal-first interaction before direct editing
- Load strategy:
  - always loaded before task execution
  - assembled into a compact prompt block by application/runtime code

`TASK_DECISION`
- Historical decision that may matter to later editing work
- Examples:
  - accepted rewrite direction
  - explicitly rejected approach
  - deferred work item that remains pending
- Load strategy:
  - not preloaded by default
  - retrieved only through `memory_search`
  - model decides when retrieval is needed

**Scope Model**

The current system does not have real `userId` or `workspaceId` concepts, so the first version should not pretend otherwise.

Use explicit memory scope fields that map to the current codebase:

- `PROFILE/default` for `USER_PROFILE`
- `DOCUMENT/<documentId>` for `TASK_DECISION`

Optional `taskId` and `sessionId` remain source metadata, not the main retrieval scope.

**Stored Memory Schema**

Milvus should store only confirmed memories. The stored record can stay compact:

- `memoryId`
- `memoryType`
- `scopeType`
- `scopeKey`
- `documentId`
- `summary`
- `details`
- `sourceTaskId`
- `sourceSessionId`
- `createdAt`
- `updatedAt`
- `embedding`

Recommended semantics:

- `summary` is the text optimized for prompt injection or tool return
- `details` contains supporting structure and source notes
- `embedding` is required for `TASK_DECISION` search; `USER_PROFILE` can still store it for future use even if the first version reads profiles by scope only

**Candidate Memory Flow**

Candidate memories should not be stored in Milvus. They are not yet trusted.

The first version should introduce a temporary in-memory pending-memory store, similar to `PendingDocumentChangeService`:

- extractor runs after task completion
- it emits candidate `USER_PROFILE` and `TASK_DECISION` items
- candidates are stored in an in-memory pending store keyed by `taskId`
- the UI/API can fetch those candidates for confirmation
- confirmation writes the selected items into Milvus
- rejection simply removes them from the pending store

This keeps the system simple while still supporting async v2 execution, where the initial submit response may return before extraction candidates are reviewed.

**Retrieval Strategy**

`USER_PROFILE`
- Retrieval is exact, not semantic, in the first version.
- Load all confirmed `PROFILE/default` memories.
- Application code trims them into a small prompt block, for example the highest-value 3-5 lines.
- These memories should be inserted into the system/context layer, not transcript memory.

`TASK_DECISION`
- Retrieval is tool-driven and semantic.
- The model gets a `memory_search` tool.
- The tool accepts a natural-language query and an optional `documentId`.
- Retrieval filters by scope first, then applies embedding similarity.
- Tool results return concise memory cards, not full transcript or chain-of-thought.

**Execution Flow**

The intended flow is:

1. Load in-memory session memory.
2. Load confirmed `USER_PROFILE` memories and assemble a prompt block.
3. Start the agent with the usual runtime and tool set.
4. Let the model call `memory_search` when prior task decisions matter.
5. Finish the task.
6. Extract candidate long-term memories from the current run.
7. Store candidates in the pending-memory store.
8. On user confirmation, write selected memories to Milvus.

**Prompt And Tool Boundaries**

The loading split is intentional:

- `USER_PROFILE` is mandatory runtime guidance, so it should not depend on tool choice.
- `TASK_DECISION` is situational evidence, so it should stay behind a tool boundary.

This prevents prompt pollution while still giving the model access to historical decisions when they are actually relevant.

**Milvus Integration**

Long-term memory should not be mixed into the existing knowledge-chunk collection. It is a different retrieval surface:

- knowledge chunks represent external document evidence
- long-term memories represent agent-learned preferences and historical decisions

The first version should create a dedicated long-term-memory repository and Milvus collection with metadata fields aligned to the memory schema above.

**API Surface**

The minimal API additions should support the confirmation loop:

- fetch pending memory candidates for a task
- confirm one or more candidates
- discard one or more candidates
- optionally inspect confirmed profile memories for debugging

The task submit APIs do not need to become memory-management APIs. Candidate review should be a separate concern.

**Review Notes**

- Do not persist short-term memory just to make the architecture look complete.
- Do not overload transcript memory with long-term semantics.
- Do not let the model directly write confirmed memories into Milvus.
- Keep `USER_PROFILE` retrieval deterministic and simple.
- Keep `TASK_DECISION` retrieval tool-based so prompt assembly stays controlled.
- Reuse the existing Milvus and embedding stack, but keep long-term memory isolated from knowledge-base storage.

# Supervisor Memory Agent Design

## Context

The current multi-agent runtime already has two different memory mechanisms:

- short-term session memory carried in `ChatTranscriptMemory`
- long-term memory infrastructure backed by `LongTermMemoryRepository`, `memorySearch`, and `memoryUpsert`

Short-term memory is already wired into the `SUPERVISOR` path, but long-term memory is not fully integrated into the worker workflow.

At first glance, the simplest idea is to let the supervisor call memory tools directly. That does not fit the current runtime contract.

The supervisor in this codebase is a structured router:

- it uses a strict JSON schema to produce `SupervisorDecision`
- it does not run through the normal tool-loop agent contract
- it must return routing JSON, not a `ToolExecutionRequest`

If memory tools are exposed directly to the supervisor, the model can mix tool intent with structured routing output. That would blur the contract between "return a routing decision" and "request tool execution", making the runtime fragile.

This design therefore keeps the supervisor tool-free and introduces a dedicated `memory` worker agent for long-term memory operations.

## Goals

- integrate long-term memory into the multi-agent supervisor workflow without changing the supervisor JSON-routing contract
- keep `USER_PROFILE` user-managed and out of autonomous agent writes
- let AI autonomously manage `DOCUMENT_DECISION` memory during execution
- support both long-term-memory retrieval and inline long-term-memory mutation in multi-agent runs
- keep downstream workers consuming summarized memory output instead of directly mutating the memory store

## Non-Goals

- no redesign of short-term session memory
- no supervisor-side direct tool execution
- no automatic agent writes for `USER_PROFILE`
- no post-task memory extraction job
- no pending-memory review queue or manual confirmation step for `DOCUMENT_DECISION`
- no broad memory write access for all workers

## Approaches Considered

### 1. Supervisor Directly Calls Memory Tools

Pros:

- centralizes decision-making in one agent

Cons:

- conflicts with the current supervisor JSON-schema contract
- creates ambiguity between routing output and tool-call intent
- requires a special runtime path just for supervisor tool execution

Rejected because it breaks the cleanest architectural boundary in the current system.

### 2. Orchestrator Pre-fetches Memory Before Supervisor

Pros:

- preserves the supervisor contract
- keeps memory retrieval outside the model

Cons:

- retrieval timing becomes rigid
- no natural place for inline memory writes
- orchestrator must decide what to retrieve before the model has clarified what matters

Useful for simple read-only context injection, but too limited for autonomous `DOCUMENT_DECISION` management.

### 3. Dedicated Memory Worker Agent

Pros:

- preserves the supervisor contract
- reuses the existing tool-loop runtime and memory tools
- fits naturally into the current worker-based multi-agent model
- provides one explicit place for long-term-memory read/write policy

Cons:

- adds another worker role and another routing choice
- requires careful write rules to avoid memory spam

Recommended.

## Recommended Design

Add a new supervisor worker named `memory`.

The supervisor remains a pure router. When the task needs historical document constraints or when stable document-level decisions should be persisted, it dispatches the `memory` worker. The `memory` worker runs as a normal tool-loop worker with access to long-term-memory tools and returns a structured summary for the rest of the workflow.

This makes long-term memory a first-class worker capability instead of a supervisor exception.

## Memory Ownership Model

The approved ownership model is:

- `USER_PROFILE`: written only by explicit user operations
- `DOCUMENT_DECISION`: automatically managed by AI

Implications:

- `USER_PROFILE` remains loaded at task initialization as `userProfileGuidance`
- the new `memory` worker must not write `USER_PROFILE`
- all autonomous memory writes in the supervisor workflow are restricted to `DOCUMENT_DECISION`

## What Counts As A `DOCUMENT_DECISION`

`DOCUMENT_DECISION` memory is constrained to rule-style, reusable document guidance.

Valid examples:

- keep the current title hierarchy for this document
- avoid speculative claims in this document
- write this document for beginners instead of experts
- do not convert this draft into marketing tone
- preserve the approved section ordering

Invalid examples:

- read section 3 before editing
- rewrote the introduction in this run
- changed three sentences in the summary
- temporary next-step instructions for the current loop

The memory system should store stable constraints and confirmed tradeoffs, not execution logs.

## Architecture

### Supervisor

The supervisor stays unchanged in principle:

- receives the full conversation snapshot and worker-result summaries
- outputs only `assign_worker` or `complete`
- never receives long-term-memory tools

This protects the JSON-schema decision contract.

### Memory Worker

Add a new worker definition such as:

- worker id: `memory`
- role: `Memory`
- capability tags: `memory`

The `memory` worker is responsible for:

- retrieving relevant `DOCUMENT_DECISION` memories
- identifying whether current document-level constraints should be persisted
- correcting stale or conflicting `DOCUMENT_DECISION` memories
- emitting a normalized summary for downstream workers

### Downstream Workers

`researcher`, `writer`, and `reviewer` should not directly mutate long-term memory in the first version.

They consume the summary produced by the `memory` worker through normal conversation memory propagation.

This keeps memory mutation centralized and reduces accidental duplication or conflicting writes.

## Runtime Flow

Recommended flow inside a supervisor task:

1. The supervisor inspects task state and decides that long-term memory matters.
2. The supervisor dispatches the `memory` worker with an instruction such as:
   - retrieve relevant prior document decisions
   - identify durable document constraints stated or implied in the current task
   - update memory only when the constraint is stable and reusable
3. The `memory` worker may call `memorySearch`.
4. The `memory` worker may call `memoryUpsert` inline during the same execution.
5. The `memory` worker returns a structured summary.
6. The supervisor receives that summary through normal worker-result summarization.
7. The supervisor dispatches writer or reviewer with that updated conversation memory.

No special orchestration path is needed for long-term memory beyond one more worker route.

## Tool Access Model

The current `MemoryToolAccessPolicy` grants memory tools only to `MAIN_WRITE`. That is not enough for the new worker-based design.

The design should evolve tool access so that the `memory` worker has its own explicit authority boundary.

Recommended direction:

- add a dedicated execution role for memory operations, or
- add a worker-aware access path that grants memory tools only to the `memory` worker

The preferred shape is a dedicated role because it scales better than hard-coding worker ids into tool policy.

Required rules:

- `memory` worker can access `memorySearch`
- `memory` worker can access `memoryUpsert`
- `writer`, `reviewer`, and `researcher` cannot access `memoryUpsert`
- `writer`, `reviewer`, and `researcher` do not gain long-term-memory authority implicitly

## Write Semantics

The write timing is inline and immediate.

That means:

- memory writes happen during the `memory` worker execution
- there is no task-end reconciliation phase
- there is no pending candidate waiting for user confirmation

The `memory` worker should prefer consistency-preserving actions:

- `CREATE` when a new stable document rule appears
- `REPLACE` when an old rule is still the same concept but needs correction
- `DELETE` when an old rule is clearly obsolete or contradicted

The worker should avoid appending near-duplicate memories when the correct action is to reuse or replace an existing one.

## Memory Worker Output Contract

The `memory` worker should not dump raw retrieval hits back into the main transcript.

Instead, it should finish with a structured summary containing only the parts that matter to downstream agents. A representative shape is:

- `confirmedConstraints`
- `deprecatedConstraints`
- `activeRisks`
- `guidanceForDownstreamWorkers`

Why this matters:

- the supervisor and downstream workers need normalized constraints, not database-shaped payloads
- raw hits would leak storage-level detail and increase prompt noise
- summary output gives one stable interface even if memory tool payloads evolve later

The exact JSON shape can be finalized during implementation planning, but it should be intentionally small and reviewable.

## Conflict Handling

The `memory` worker is the place where document-level memory conflicts are resolved.

Expected cases:

- retrieved memory agrees with current task direction
  - keep it
  - summarize it
- retrieved memory partially conflicts with current instruction
  - replace it if the new direction supersedes the old one
- retrieved memory is clearly obsolete
  - delete it
- no relevant memory exists
  - continue without forced write

The worker prompt should be conservative:

- write only stable, reusable constraints
- do not write transient execution steps
- prefer no write over speculative write

## Failure Handling

Failure behavior should be non-fatal whenever possible.

### Memory Search Failure

- return a structured "memory unavailable" or "no relevant memory" summary
- allow supervisor flow to continue

### Memory Upsert Validation Failure

- surface the structured tool error back to the `memory` worker
- let the worker self-correct within the same tool loop

### Long-Term-Memory Repository Not Configured

- the worker should degrade to retrieval-unavailable behavior
- the supervisor should still be able to continue routing other workers

The workflow should lose memory enhancement, not lose the whole task.

## Prompting Rules

The `memory` worker prompt must explicitly encode the approved product policy:

- never write `USER_PROFILE`
- only manage `DOCUMENT_DECISION`
- only store durable document constraints
- avoid duplicate or overly specific memories
- prefer `REPLACE` or `DELETE` over unbounded accumulation
- summarize decisions for downstream workers in concise, structured form

This prompt is the primary guardrail against memory pollution.

## Testing Strategy

### Routing Tests

Add tests that verify:

- the supervisor can route to the `memory` worker
- a completed `memory` worker result is summarized into supervisor-visible memory

### Tool Access Tests

Add tests that verify:

- the `memory` worker sees `memorySearch` and `memoryUpsert`
- `writer`, `reviewer`, and `researcher` do not see memory write tools

### Behavior Tests

Add tests that verify:

- only `DOCUMENT_DECISION` writes are allowed through the `memory` worker path
- the worker prefers `REPLACE` and `DELETE` when handling stale memory
- no write happens for transient execution-only instructions

### End-to-End Tests

Add a focused supervisor workflow test where:

- a prior `DOCUMENT_DECISION` exists
- the supervisor routes to `memory`
- the `memory` worker retrieves and normalizes it
- the worker performs an inline correction
- the writer receives the summarized constraint through conversation memory

## Risks And Mitigations

- Risk: memory spam from over-eager writes
  - Mitigation: rule-style memory definition, conservative prompt, focused tests for duplicate and transient writes

- Risk: worker proliferation makes routing harder
  - Mitigation: keep the `memory` worker narrow and only invoke it when memory actually matters

- Risk: storage details leak into prompts
  - Mitigation: require normalized summary output instead of raw search results

- Risk: writer or reviewer still need direct memory access later
  - Mitigation: start with centralized memory authority; expand later only if specific failures justify it

## Success Criteria

- the supervisor remains a pure JSON-routing agent
- long-term memory is available in multi-agent workflows through a dedicated `memory` worker
- AI writes only `DOCUMENT_DECISION`
- writes happen inline during the `memory` worker run
- downstream workers consume summarized memory constraints without direct memory mutation

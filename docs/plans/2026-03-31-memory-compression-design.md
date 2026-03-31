# Memory Compression Design

**Context**

The v2 runtime already has a shared `MemoryCompressor`, but compression is still applied too late in most flows: `ContextFactory.buildModelInvocationContext(...)` compresses memory only for the current model call, while the runtime `AgentRunContext` often continues to carry the original transcript. This creates two problems:

- compression is not reflected in the runtime state that later orchestration steps see
- production code keeps test-oriented `passthrough` fallbacks and no-arg constructors just to avoid wiring a real compressor

The new requirement is to move compression earlier so it happens when a `ContextFactory` creates or derives an `AgentRunContext`, and to remove test-only no-op production paths.

**Goals**

- Make compression part of context construction, not only model-message assembly.
- Ensure compressed memory is written back into runtime `AgentRunContext`.
- Keep `buildModelInvocationContext(...)` focused on mapping an already-prepared context to model messages.
- Remove `passthrough` no-op helpers and production no-arg constructors that exist only for tests.
- Keep `MemoryCompressor` as the single compression abstraction.
- Preserve current compression semantics:
  - `system prompt` stays outside transcript memory
  - latest raw messages are preserved according to configuration
  - observed total-token usage remains transcript-level metadata

**Non-Goals**

- No tokenizer integration
- No new execution-memory subtype
- No mutation-heavy hidden side effects on existing `AgentRunContext` instances
- No redesign of `ChatMessage` kinds
- No broad rewrite of runtime/orchestrator flow outside memory preparation boundaries

**Design Decision**

Compression should happen when a `ContextFactory` produces a new runtime context, not when it merely maps memory into LangChain4j messages.

That means:

1. `prepareInitialContext(...)` and all other context-derivation methods must compress before returning the new `AgentRunContext`.
2. `buildModelInvocationContext(...)` must become a pure read-only mapping step.
3. The resulting `AgentRunContext` must carry compressed memory forward into runtime/orchestrator code.

This is a better fit for the factory role: context engineering should own both runtime-state shaping and model-input shaping.

**Runtime Write-Back Strategy**

`AgentRunContext` will gain a `withMemory(...)` style method so factories can return a new context with compressed memory in an explicit, immutable-style way.

Factories should use a small internal flow:

1. create or derive the next `AgentRunContext`
2. if memory is `ChatTranscriptMemory`, compress it through `MemoryCompressor`
3. return a new context containing the compressed transcript

This keeps compression explicit and avoids mutating incoming objects in place.

**Where Compression Must Move**

Compression must be applied anywhere a factory creates or derives a new `AgentRunContext`, including:

- `ReactAgentContextFactory.prepareInitialContext(...)`
- `PlanningAgentContextFactory.prepareInitialContext(...)`
- `PlanningAgentContextFactory.prepareExecutionInitialContext(...)`
- `PlanningAgentContextFactory.prepareExecutionStepContext(...)`
- `PlanningAgentContextFactory.summarizeCompletedStep(...)`
- `ReflexionActorContextFactory.prepareRevisionContext(...)`
- `ReflexionCriticContextFactory.prepareInitialContext(...)`
- `ReflexionCriticContextFactory.prepareReviewContext(...)`
- `SupervisorContextFactory.prepareInitialContext(...)`
- `SupervisorContextFactory.buildWorkerExecutionContext(...)`
- `SupervisorContextFactory.summarizeWorkerResult(...)`
- supervisor worker context-factory `prepareInitialContext(...)` methods

This list matters because moving compression only into `prepareInitialContext(...)` would still leave later append/derive paths carrying uncompressed memory.

**What `buildModelInvocationContext(...)` Should Do After The Change**

After the refactor, `buildModelInvocationContext(...)` should:

- read the already-prepared `context.getMemory()`
- prepend any fixed `system prompt`
- map runtime memory into model messages
- optionally add response-format metadata

It should not call `MemoryCompressor` anymore.

**MemoryCompressor API Usage**

The public compression abstraction remains `MemoryCompressor`.

It should provide shared convenience for transcript handling so factories do not duplicate:

- type checks for `ChatTranscriptMemory`
- request construction
- null-safe fallback to original memory

That shared logic belongs in the compressor boundary, not in a new helper class.

**Removing `passthrough`**

The current `passthrough` methods are no-op compressors kept only so production classes can offer no-arg constructors for tests. They should be removed.

After the refactor:

- production constructors require a real `MemoryCompressor`
- tests explicitly pass stub or lambda compressors

This keeps test scaffolding out of production code and makes dependencies honest.

**Session Persistence**

The desired steady state is that `ContextFactory` write-back makes runtime memory already compressed before orchestration returns, so persistence naturally receives compact memory.

During the refactor, `SessionMemoryTaskOrchestrator` may keep save-time compression as a temporary safety net if there are still runtime paths that append transcript messages outside factory-controlled derivation. Once tests show every path writes back compressed memory before returning, that fallback can be removed.

**Review Notes**

- `ContextFactory` should own context shaping end-to-end: runtime context first, model messages second.
- Compression must not be a transient view that disappears after one model call.
- Keeping no-op compressor helpers in production code weakens constructor contracts and should be cleaned up.
- `buildModelInvocationContext(...)` becomes simpler and easier to reason about once compression is moved earlier.

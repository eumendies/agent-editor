# Session Memory Design

**Goal:** Persist multi-turn conversation memory by `sessionId` with an in-memory store, while keeping `DefaultExecutionRuntime` responsible only for appending messages produced during a single run.

**Design:**
- Add `SessionMemoryStore` with an `InMemorySessionMemoryStore` implementation keyed by `sessionId`.
- Keep session-memory load/save in the task orchestration layer rather than inside runtime.
- Extend task orchestration contracts so a top-level orchestrator can receive prior memory and return final memory.
- Keep `DefaultExecutionRuntime` responsible for appending per-run transcript messages:
  - `UserChatMessage` at the start of a run
  - `AiToolCallChatMessage`
  - `ToolExecutionResultChatMessage`
  - `AiChatMessage` for `Complete` and `Respond`

**Rationale:**
- Runtime already owns the transcript generated inside one execution loop.
- Session memory persistence is a higher-level concern and should not be mixed into the runtime loop.
- This separation avoids hard-wiring `sessionId` persistence into nested runtime calls.

**Files:**
- Modify `src/main/java/com/agent/editor/agent/v2/core/runtime/DefaultExecutionRuntime.java`
- Create `src/main/java/com/agent/editor/agent/v2/task/SessionMemoryStore.java`
- Create `src/main/java/com/agent/editor/agent/v2/task/InMemorySessionMemoryStore.java`
- Create `src/main/java/com/agent/editor/agent/v2/task/SessionMemoryTaskOrchestrator.java`
- Modify `src/main/java/com/agent/editor/agent/v2/task/TaskRequest.java`
- Modify `src/main/java/com/agent/editor/agent/v2/task/TaskResult.java`
- Modify `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`

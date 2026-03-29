# Structured Output Parser Relocation Design

## Goal

Move structured JSON output parsing out of the supervisor-specific package into a shared `agent/v2` utility so supervisor and reflexion agents use one consistent parsing strategy, including markdown code-fence cleanup fallback.

## Options

### Option 1: Shared utility under `agent/v2/util`

- Create a general utility for strict parse, tolerant parse, and markdown fence stripping.
- Replace duplicated parsing in supervisor workers, supervisor routing, and reflexion critic.

Pros:
- Clean dependency direction.
- Smallest abstraction that solves the duplication.
- Easy to test in isolation.

Cons:
- Leaves some agent-level request flow duplication untouched.

### Option 2: Put parser in `core`

- Treat structured parsing as runtime infrastructure.

Pros:
- More central package placement.

Cons:
- Overstates the role of this logic.
- Parsing strategy is agent-output-oriented, not core runtime behavior.

### Option 3: Injectable parser component

- Make parser behavior a Spring bean with shared mapper configuration.

Pros:
- Highly configurable.

Cons:
- Too much ceremony for a pure function helper.

## Recommendation

Use Option 1. This keeps the utility broadly reusable without inflating the runtime or DI surface.

## Design

### Shared utility

- Add `com.agent.editor.agent.v2.util.StructuredOutputParsers`.
- Provide:
  - `parseJson(...)`
  - `parseJsonWithMarkdownCleanup(...)`
  - `parseJsonOrThrow(...)`
  - internal `stripMarkdownCodeFence(...)`

### Replacement scope

- Replace supervisor worker parsing for `EvidencePackage` and `ReviewerFeedback`.
- Replace `HybridSupervisorAgent` parsing for both `ReviewerFeedback` and `SupervisorRoutingResponse`.
- Replace `ReflexionCritic` parsing for both tolerant retry parsing and strict public parsing.
- Delete the supervisor-local parser utility.

### Behavior

- Tolerant parse first tries raw JSON.
- If raw parse fails, retry after stripping a full markdown code fence wrapper.
- Strict parse also benefits from the same cleanup path, but throws if parsing still fails.

### Testing

- Move parser utility tests to the new util package.
- Add reflexion tests for fenced JSON parsing.
- Add supervisor routing test for fenced routing response parsing.
- Keep existing worker and runtime regressions green.

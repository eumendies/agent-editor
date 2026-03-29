# Structured Completion Parsing Design

## Goal

Make supervisor workers more tolerant of LLM JSON formatting mistakes by preventing markdown-fenced reviewer output where possible and by adding a shared fallback parser that can strip markdown code fences before retrying structured deserialization.

## Approach Options

### Option 1: Shared parser utility only

- Add a `StructuredCompletionParsers` utility for direct JSON parsing and markdown-fence cleanup retry.
- Update `EvidenceReviewerAgent` and `ResearcherAgent` to use it.
- Strengthen reviewer system prompt to forbid markdown fences.

Pros:
- Smallest behavior-focused change.
- Solves the current failure mode directly.
- Avoids unnecessary inheritance refactor.

Cons:
- Leaves request/decision flow duplication across workers in place.

### Option 2: Abstract worker base class plus shared parser

- Add the parser utility.
- Also move common chat request / tool-call / completion flow into an abstract base class.

Pros:
- Removes more duplication.

Cons:
- Larger behavioral surface.
- Mixes a bugfix with a structural refactor.
- Harder to validate safely in one pass.

## Recommendation

Use Option 1. The current requirement is about prompt hardening and tolerant structured parsing, not about re-architecting all worker agents.

## Design

### Prompt changes

- In `EvidenceReviewerAgentContextFactory`, update the system prompt to explicitly require raw JSON output.
- Add a hard prohibition against markdown fences, backticks, or explanatory prose around JSON.

### Shared parser

- Introduce `StructuredCompletionParsers` under the supervisor worker package.
- Provide:
  - direct JSON deserialization into a target class
  - fallback deserialization after stripping surrounding markdown code fences
  - conservative cleanup only for full-response fenced JSON blocks

### Worker integration

- `EvidenceReviewerAgent` uses the shared parser for `ReviewerFeedback`.
- `ResearcherAgent` uses the same parser for `EvidencePackage`.
- `GroundedWriterAgent` remains string-based because there is no typed writer result contract yet.

### Testing

- Add a reviewer test covering fenced JSON output from the model.
- Tighten reviewer context factory test to assert the prompt forbids markdown fences.
- Add parser unit tests for fence cleanup retry.
- Keep existing researcher and reviewer structured completion tests green.

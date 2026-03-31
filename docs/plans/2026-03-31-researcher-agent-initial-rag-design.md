# Researcher Agent Initial RAG Design

**Context**

`ResearcherAgent` currently delegates the whole retrieval strategy to the model on every turn. That means the first retrieval query may already be rewritten away from the user's original instruction before any evidence is collected.

This change makes the first RAG retrieval deterministic: the first retrieval must always use the user's raw instruction. After that first retrieval result is available in memory, the model regains control and can decide whether to stop, rewrite the query, or split the next retrieval into multiple parallel queries.

**Goals**

- Force the first `retrieveKnowledge` call to use the current task instruction as-is.
- Keep later retrieval decisions model-driven.
- Minimize code churn by containing the behavior change inside `ResearcherAgent`.

**Architecture**

`ResearcherAgent.decide(...)` will add an early branch before model invocation:

1. If `chatModel` is `null`, keep the existing stub behavior unchanged.
2. If the execution memory does not yet contain a `retrieveKnowledge` tool result, return a deterministic `ToolLoopDecision.ToolCalls` with a single `retrieveKnowledge` call whose `query` equals `ExecutionRequest.instruction`.
3. Once at least one `retrieveKnowledge` tool result exists in memory, fall back to the current model-driven flow:
   - build invocation context
   - call the model
   - convert tool requests into `ToolLoopDecision.ToolCalls`
   - or parse final `EvidencePackage`

This keeps the first retrieval policy in code instead of prompt text, while preserving the existing adaptive retrieval loop after initial grounding.

**Decision Rules**

- First retrieval detection only checks executed tool results in memory, not prior AI tool requests.
- The first deterministic tool decision always contains exactly one `retrieveKnowledge` call.
- The `query` payload is the original task instruction without agent-side rewriting.
- Later turns may:
  - request another `retrieveKnowledge` with a rewritten query
  - request multiple `retrieveKnowledge` calls for query decomposition
  - complete directly with an `EvidencePackage`

**Why Memory-Based Detection**

Using executed tool results as the gate avoids false positives. If the model previously suggested a retrieval call but the loop never executed it, the agent must still treat the next turn as “no initial retrieval has happened yet”.

**Error Handling**

- Blank instructions are still passed through unchanged to the tool call. The agent should not invent fallback queries.
- Structured output parsing remains unchanged.
- Existing stub mode remains unchanged.

**Testing Strategy**

Add or adjust tests around these behaviors:

1. First turn with only user instruction in memory:
   - returns `ToolLoopDecision.ToolCalls`
   - contains one `retrieveKnowledge` call
   - uses the raw instruction as `query`
   - does not invoke the model
2. Later turn after at least one `retrieveKnowledge` result:
   - invokes the model
   - preserves current tool-call conversion behavior
   - preserves current completion parsing behavior
3. Existing tests that assumed the first turn immediately invoked the model must be updated to include a prior retrieval result in memory when they are asserting post-initial behavior.

**Implementation Scope**

- Modify `src/main/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgent.java`
- Update `src/test/java/com/agent/editor/agent/v2/supervisor/worker/ResearcherAgentTest.java`
- Leave `ResearcherAgentContextFactory` unchanged unless a test reveals a hidden dependency

**Review Notes**

- 中文注释应加在首轮固定检索分支附近，说明这里先锁定用户原始指令做首轮召回，避免模型在无证据前过早改写查询导致召回偏移。
- 不要把首轮固定策略塞回 prompt；这次需求要求通过确定性的 `ToolLoopDecision.ToolCalls` 实现。

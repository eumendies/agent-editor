# React Document Write Default Design

**Context**

The current ReAct v2 prompt allows the model to answer directly in chat even when the user is effectively asking it to write document content. For this repository, the desired default is stricter: when the user asks the agent to write, expand, rewrite, polish, or generate content, the agent should treat that as a document-editing task and call `editDocument` instead of returning the draft in chat.

**Decision**

- Scope this change to `ReactAgentDefinition.buildSystemPrompt()`.
- Do not change runtime behavior, task modes, or message assembly.
- Do not touch the commented `UserMessage` line in `buildMessages()`.

**Behavior Rules**

- Writing-oriented instructions should default to editing the current document.
- If the user does not specify an insertion position, the agent should generate the full updated document and use `editDocument` to overwrite the whole document.
- Direct chat responses remain allowed only for explanation, analysis, comparison, or other non-editing requests.
- After a writing task, the final text should only briefly confirm that the document was updated.

**Risks**

- This is still prompt-only guidance, not a hard runtime guarantee.
- Model compliance should improve, but occasional direct chat output may still happen.


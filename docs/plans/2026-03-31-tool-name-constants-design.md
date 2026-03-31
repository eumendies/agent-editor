# Tool Name Constants Design

**Context**

Document-oriented tool names such as `editDocument`, `appendToDocument`, `getDocumentSnapshot`, `searchContent`, `analyzeDocument`, and `retrieveKnowledge` are currently repeated as raw strings across production code and tests. The duplication is especially visible in `SupervisorAgentConfig`, but it also appears in tool implementations, orchestrators, and unit tests that assert allowed tool sets.

This makes renames and consistency checks more expensive than they should be. A typo in one call site can silently drift from the canonical tool implementation name.

**Goals**

- Introduce a single source of truth for document tool names.
- Replace raw string literals in production code that refer to these tool names.
- Update directly related tests to assert against the shared constants instead of repeating literals.
- Keep scope bounded: do not perform a whole-repo string-replacement sweep for unrelated display text or protocol snapshots.

**Recommended Approach**

Create a centralized constants class at:

- `src/main/java/com/agent/editor/agent/v2/tool/document/DocumentToolNames.java`

The class should define public constants for:

- `EDIT_DOCUMENT`
- `APPEND_TO_DOCUMENT`
- `GET_DOCUMENT_SNAPSHOT`
- `SEARCH_CONTENT`
- `ANALYZE_DOCUMENT`
- `RETRIEVE_KNOWLEDGE`

**Why This Approach**

This keeps the names easy to import and avoids scattering references across six different tool classes. Compared with putting one `NAME` constant on each tool class, a centralized constants class is simpler to use from configuration classes and orchestrators that need several tool names at once.

**Production Code Scope**

Update raw tool-name strings in these production areas:

1. `SupervisorAgentConfig`
   - worker allowed-tools lists
2. document tool implementations
   - `name()` methods should return the matching constant
3. `ResearcherAgent`
   - initial deterministic retrieval call
   - memory scan that identifies `retrieveKnowledge`
4. `ReflexionOrchestrator`
   - actor and critic allowed-tools lists

**Test Scope**

Update tests that directly verify the tool names or ordered allowed-tools lists for the affected production code, including:

- `AgentV2ConfigurationSplitTest`
- `ResearcherAgentTest`
- `ResearcherAgentContextFactoryTest`
- `GroundedWriterAgentTest`
- `EvidenceReviewerAgentTest`
- `ReflexionOrchestratorTest`
- document tool unit tests
- other closely related tests that explicitly assert the affected tool names

Do not expand scope into unrelated event-display strings or free-form text where a raw literal is not actually acting as a shared identifier.

**Non-Goals**

- No enum migration
- No protocol redesign
- No broad replacement of every string occurrence in the repository
- No changes to tool argument schemas or behavior

**Review Notes**

- 常量类应放在 document tool 包内，表明它服务的是这一组文档工具标识，而不是全局任意字符串常量。
- 对顺序敏感的 allowed-tools 列表测试仍应保留顺序断言，只是把字面量改为常量引用，避免把这次重构变成行为变更。

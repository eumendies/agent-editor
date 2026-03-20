# Session Memory Debug Endpoint Design

**Goal:** Expose the current session memory for debugging multi-turn conversations.

**Route:** `GET /api/v1/agent/session/{sessionId}/memory`

**Why this location:** The endpoint is operationally tied to the agent session lifecycle, so keeping it under `AgentController` is the smallest and clearest change.

**Response shape:**
- `sessionId`
- `messageCount`
- `messages`

Each message includes:
- `type`
- `text`
- optional `toolCallId`
- optional `toolName`
- optional `arguments`
- optional `toolCalls` for `AiToolCallChatMessage`

**Implementation outline:**
- Add a read method to `SessionMemoryStore`
- Add DTOs for session-memory response
- Add a small mapping service that converts `ChatMessage` to API DTOs
- Add a controller endpoint in `AgentController`

**Files:**
- Modify `src/main/java/com/agent/editor/agent/v2/task/SessionMemoryStore.java`
- Create `src/main/java/com/agent/editor/dto/SessionMemoryResponse.java`
- Create `src/main/java/com/agent/editor/dto/SessionMemoryMessageResponse.java`
- Create `src/main/java/com/agent/editor/dto/SessionMemoryToolCallResponse.java`
- Create `src/main/java/com/agent/editor/service/SessionMemoryQueryService.java`
- Modify `src/main/java/com/agent/editor/controller/AgentController.java`
- Test `src/test/java/com/agent/editor/service/SessionMemoryQueryServiceTest.java`
- Test `src/test/java/com/agent/editor/controller/AgentControllerTest.java`

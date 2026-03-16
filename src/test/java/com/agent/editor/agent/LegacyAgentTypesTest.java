package com.agent.editor.agent;

import com.agent.editor.model.AgentMode;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LegacyAgentTypesTest {

    @Test
    void shouldMarkLegacyAgentTypesAsDeprecated() {
        assertTrue(AgentFactory.class.isAnnotationPresent(Deprecated.class));
        assertTrue(BaseAgent.class.isAnnotationPresent(Deprecated.class));
        assertTrue(ReActAgent.class.isAnnotationPresent(Deprecated.class));
        assertTrue(EditorAgentTools.class.isAnnotationPresent(Deprecated.class));
    }

    @Test
    void shouldStillCreateLegacyEditorAgentToolsWithTwoArguments() throws Exception {
        AgentState agentState = new AgentState(null, "rewrite", AgentMode.REACT);
        WebSocketService webSocketService = mock(WebSocketService.class);

        EditorAgentTools tools = new EditorAgentTools(agentState, webSocketService);

        assertNotNull(tools);
        assertEquals(agentState, readField(tools, "agentState"));
        assertEquals(webSocketService, readField(tools, "webSocketService"));
    }

    @Test
    void shouldReturnReactAgentFromLegacyFactory() throws Exception {
        AgentFactory agentFactory = new AgentFactory();
        setField(agentFactory, "chatModel", mock(ChatModel.class));
        setField(agentFactory, "websocketService", mock(WebSocketService.class));

        AgentExecutor agent = agentFactory.getAgent(AgentMode.PLANNING);

        assertInstanceOf(ReActAgent.class, agent);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AgentFactory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = EditorAgentTools.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

package com.agent.editor.agent.v2.core.context;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelInvocationContextTest {

    @Test
    void shouldBeJavaBeanInsteadOfRecordAndCopyMutableLists() {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("hello")));
        List<ToolSpecification> toolSpecifications = new ArrayList<>(List.of(
                ToolSpecification.builder().name("search").description("search").build()
        ));

        ModelInvocationContext context = new ModelInvocationContext(messages, toolSpecifications, null);
        messages.clear();
        toolSpecifications.clear();

        assertFalse(ModelInvocationContext.class.isRecord());
        assertEquals(1, context.getMessages().size());
        assertEquals(1, context.getToolSpecifications().size());
    }
}

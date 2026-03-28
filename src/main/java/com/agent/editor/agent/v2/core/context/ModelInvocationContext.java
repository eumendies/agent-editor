package com.agent.editor.agent.v2.core.context;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ModelInvocationContext {

    private List<ChatMessage> messages = List.of();
    private List<ToolSpecification> toolSpecifications = List.of();
    private ResponseFormat responseFormat;

    public ModelInvocationContext(List<ChatMessage> messages,
                                  List<ToolSpecification> toolSpecifications,
                                  ResponseFormat responseFormat) {
        setMessages(messages);
        setToolSpecifications(toolSpecifications);
        this.responseFormat = responseFormat;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public void setToolSpecifications(List<ToolSpecification> toolSpecifications) {
        this.toolSpecifications = toolSpecifications == null ? List.of() : List.copyOf(toolSpecifications);
    }
}

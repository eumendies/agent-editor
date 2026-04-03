package com.agent.editor.agent.v2.core.context;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次模型调用前整理出的输入上下文，统一承载消息、工具和响应格式。
 */
@Data
@NoArgsConstructor
public class ModelInvocationContext {

    // 发送给模型的消息序列。
    private List<ChatMessage> messages = List.of();
    // 本次调用允许使用的工具规格。
    private List<ToolSpecification> toolSpecifications = List.of();
    // 结构化输出或约束输出格式。
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

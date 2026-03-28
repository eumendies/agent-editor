package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.*;
import com.agent.editor.agent.v2.core.exception.NullChatModelException;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.mapper.ExecutionMemoryChatMessageMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

public class ReactAgent implements ToolLoopAgent {

    private final ChatModel chatModel;
    private final ExecutionMemoryChatMessageMapper memoryChatMessageMapper;

    public ReactAgent(ChatModel chatModel) {
        this(chatModel, new ExecutionMemoryChatMessageMapper());
    }

    ReactAgent(ChatModel chatModel,
               ExecutionMemoryChatMessageMapper memoryChatMessageMapper) {
        this.chatModel = chatModel;
        this.memoryChatMessageMapper = memoryChatMessageMapper;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public ToolLoopDecision decide(AgentRunContext context) throws NullChatModelException {
        if (chatModel == null) {
            throw new NullChatModelException("ChatModel of ReactAgent is not provided");
        }

        // ReAct 在 v2 里仍然是“单轮决策器”，真正的循环由 runtime 负责。
        String systemPrompt = buildSystemPrompt();

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(buildMessages(context, systemPrompt))
                .toolSpecifications(context.getToolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            // 这里不直接执行工具，只把调用意图翻译成统一 Decision，由 runtime 接管后续步骤。
            return new ToolLoopDecision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        return new ToolLoopDecision.Complete<String>(aiMessage.text(), aiMessage.text());
    }

    private String buildSystemPrompt() {
        return """
                You are a ReAct-style document editing agent.
                Think step by step:
                1. Analyze the user's instruction.
                2. Take ONE action at a time using the available tools when an action is needed.
                3. Observe the result of that action.
                4. Decide whether to continue with another action or finish.
                Your primary job is to update the current document when the user asks you to write.
                If the user asks you to write, draft, rewrite, expand, polish, or generate content, you must call editDocument instead of returning the drafted content directly in chat.
                If the user does not specify a target location, generate the full updated document and use editDocument to overwrite the entire document.
                Only reply directly in chat when the user explicitly wants you to explain, analyze, answer questions, or discuss options without editing the document.
                After completing a document-writing task, keep your final text concise and only confirm that the document was updated.
                """;
    }

    private List<ChatMessage> buildMessages(AgentRunContext context, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        // UserMessage保存在AgentRunContext的memory里
        messages.addAll(memoryChatMessageMapper.toChatMessages(context.state().getMemory()));
        return messages;
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }
}

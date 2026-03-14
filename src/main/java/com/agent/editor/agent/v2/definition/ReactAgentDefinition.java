package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.stream.Collectors;

public class ReactAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;

    public ReactAgentDefinition(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AgentType type() {
        return AgentType.REACT;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        if (chatModel == null) {
            return new Decision.Complete("placeholder", "react stub");
        }

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(buildSystemPrompt()),
                        UserMessage.from(buildUserPrompt(context))
                ))
                .toolSpecifications(context.toolSpecifications())
                .build());

        AiMessage aiMessage = response.aiMessage();
        if (aiMessage.hasToolExecutionRequests()) {
            return new Decision.ToolCalls(
                    aiMessage.toolExecutionRequests().stream()
                            .map(this::toToolCall)
                            .toList(),
                    aiMessage.text()
            );
        }

        return new Decision.Complete(aiMessage.text(), aiMessage.text());
    }

    private String buildSystemPrompt() {
        return """
                You are a ReAct-style document editing agent.
                Decide whether to finish directly or call a tool.
                Return concise final text when no tool call is needed.
                """;
    }

    private String buildUserPrompt(ExecutionContext context) {
        String currentContent = context.state().currentContent() != null
                ? context.state().currentContent()
                : context.request().document().content();
        String toolResultBlock = context.state().toolResults().isEmpty()
                ? ""
                : """

                Previous tool results:
                %s

                Use the updated document state and these observations before deciding whether another tool call is necessary.
                """.formatted(renderToolResults(context));
        return """
                Document:
                %s

                Instruction:
                %s
                %s
                """.formatted(
                currentContent,
                context.request().instruction(),
                toolResultBlock
        );
    }

    private String renderToolResults(ExecutionContext context) {
        return context.state().toolResults().stream()
                .map(result -> "- " + result.message())
                .collect(Collectors.joining("\n"));
    }

    private ToolCall toToolCall(ToolExecutionRequest request) {
        return new ToolCall(request.name(), request.arguments());
    }
}

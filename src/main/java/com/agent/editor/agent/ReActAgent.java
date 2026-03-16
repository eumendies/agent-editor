package com.agent.editor.agent;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.*;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.experimental.SuperBuilder;

import java.util.*;

@SuperBuilder
@Deprecated(forRemoval = false)
public class ReActAgent extends BaseAgent {
//
//    public ReActAgent() {
//    }
//
//    public ReActAgent(ChatModel chatLanguageModel, WebSocketService websocketService) {
//        this.chatLanguageModel = chatLanguageModel;
//        this.websocketService = websocketService;
//    }

    @Override
    public AgentMode getMode() {
        return AgentMode.REACT;
    }


    @Override
    protected String buildSystemPrompt() {
        return """
            You are an AI-powered document editing agent using the ReAct (Reasoning + Acting) pattern.
            Your goal is to understand user instructions and edit the document accordingly.
            
            ## ReAct Pattern
            Think step by step:
            1. Analyze the user's instruction
            2. Take ONE action at a time using the available tools
            3. Observe the result
            4. Continue or complete based on the result
            5. Complete the task by calling terminateTask function when finished
            """;
    }

    @Override
    protected AgentStep createStep(AgentState state, AgentStepType type, 
                                     String content, Map<String, Object> metadata) {
        AgentStep step = new AgentStep(
            UUID.randomUUID().toString(),
            state.getTaskId(),
            state.getCurrentStep(),
            type
        );
        step.setThought(content);
        step.setResult(content);
        step.setMetadata(metadata);
        
        if (type == AgentStepType.COMPLETED) {
            step.setFinal(true);
        }
        
        return step;
    }

    @Override
    protected AgentStepType parseResponse(AiMessage aiMessage, AgentState state) {
        if (!aiMessage.hasToolExecutionRequests()) {
            String lower = aiMessage.text().toLowerCase();
            if (lower.contains("done") || lower.contains("task complete")) {
                return AgentStepType.COMPLETED;
            }
            return AgentStepType.THINKING;
        }
        
        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        for (ToolExecutionRequest request : requests) {
            if ("terminateTask".equals(request.name())) {
                return AgentStepType.COMPLETED;
            }
        }
        return AgentStepType.ACTION;
    }

    @Override
    protected String extractContent(AiMessage aiMessage, AgentStepType stepType) {
        if (stepType == AgentStepType.ACTION && aiMessage.hasToolExecutionRequests()) {
            StringBuilder content = new StringBuilder();
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                content.append("ACTION: ").append(request.name()).append("\n");
            }
            return content.toString();
        }
        
        if (stepType == AgentStepType.COMPLETED) {
            return "Task completed";
        }
        
        return aiMessage.text();
    }

    @Override
    public void sendStepUpdate(AgentState state, AgentStep step) {
        if (websocketService != null && state.getSessionId() != null) {
            String content = step.getThought() != null ? step.getThought() : 
                           step.getResult() != null ? step.getResult() : "";
            websocketService.sendToSession(state.getSessionId(), 
                WebSocketMessage.step(state.getTaskId(), step.getType(), content));
        }
    }
}

package com.agent.editor.agent;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PlanningAgent extends BaseAgent {

    @Override
    public AgentMode getMode() {
        return AgentMode.PLANNING;
    }

    @Override
    protected List<ToolSpecification> buildTools() {
        return AgentTools.defaultTools();
    }

    @Override
    protected String buildSystemPrompt() {
        return """
            You are an AI-powered document editing agent using the Planning pattern.
            Your goal is to create and execute a structured plan to fulfill user instructions.
            
            ## Planning Pattern
            1. First, create a detailed execution plan (PLANNING phase)
            2. Then execute each step one by one (EXECUTION phase)
            3. Validate each step's result
            4. When all steps are done, call terminateTask function (COMPLETED phase)
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
            if (lower.contains("done") || lower.contains("task complete") || 
                lower.contains("plan complete")) {
                return AgentStepType.COMPLETED;
            }
            if (lower.contains("plan:") || lower.contains("planning:")) {
                return AgentStepType.PLANNING;
            }
            return AgentStepType.THINKING;
        }
        
        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        for (ToolExecutionRequest request : requests) {
            if (AgentTools.TERMINATE_TASK.equalsIgnoreCase(request.name())) {
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

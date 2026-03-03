package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.WebSocketMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlanningAgent extends BaseAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(PlanningAgent.class);
    
    @Autowired
    private ChatModel chatLanguageModel;
    
    private static final Pattern PLAN_PATTERN = Pattern.compile(
        "(?:PLAN|PLANNING):\\s*(.*?)(?:EXECUTION|$)", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern EXECUTION_PATTERN = Pattern.compile(
        "(?:EXECUTION|NEXT STEP):\\s*(.*?)(?:$)", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern COMPLETED_PATTERN = Pattern.compile(
        "(?:COMPLETED|FINISHED|DONE):\\s*(.*)", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public AgentMode getMode() {
        return AgentMode.PLANNING;
    }

    @Override
    protected List<ToolSpecification> buildTools() {
        return Arrays.asList(
            ToolSpecification.builder()
                .name("readDocument")
                .description("Read the current document content")
                .build(),
            ToolSpecification.builder()
                .name("editDocument")
                .description("Edit the document content with specified changes")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("content", "The new content to replace the document")
                    .required("content")
                    .build())
                .build(),
            ToolSpecification.builder()
                .name("searchContent")
                .description("Search for specific text in the document")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("pattern", "The text pattern to search for")
                    .required("pattern")
                    .build())
                .build(),
            ToolSpecification.builder()
                .name("formatDocument")
                .description("Format the document with indentation")
                .build(),
            ToolSpecification.builder()
                .name("analyzeDocument")
                .description("Analyze the document for word count, line count, etc.")
                .build(),
            ToolSpecification.builder()
                .name("undoChange")
                .description("Undo the last change")
                .build(),
            ToolSpecification.builder()
                .name("previewChanges")
                .description("Preview changes before applying")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("content", "Content to preview")
                    .required("content")
                    .build())
                .build(),
            ToolSpecification.builder()
                .name("compareVersions")
                .description("Compare current document with original")
                .build(),
            ToolSpecification.builder()
                .name("terminateTask")
                .description("Terminate the current task immediately")
                .build(),
            ToolSpecification.builder()
                .name("respondToUser")
                .description("Send a message directly to the user. Use this when you want to communicate with the user without modifying the document, such as asking for clarification, providing summaries, or explaining what you're doing.")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("message", "The message to send to the user")
                    .required("message")
                    .build())
                .build()
        );
    }

    @Override
    protected String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI-powered document editing agent using the Planning pattern.\n");
        prompt.append("Your goal is to create and execute a structured plan to fulfill user instructions.\n\n");
        
        prompt.append("## Planning Pattern\n");
        prompt.append("1. First, create a detailed execution plan (PLANNING phase)\n");
        prompt.append("2. Then execute each step one by one (EXECUTION phase)\n");
        prompt.append("3. Validate each step's result\n");
        prompt.append("4. When all steps are done, provide the final result (COMPLETED phase)\n\n");
        
        logger.info("【Planning系统Prompt构建完成】, 长度: {}", prompt.length());
        
        return prompt.toString();
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
        
        if (type == AgentStepType.COMPLETED) {
            step.setFinal(true);
        }
        
        return step;
    }
    
    @Override
    protected AgentStepType parseResponse(AiMessage aiMessage, AgentState state) {
        String response = aiMessage.text();
        String lower = response.toLowerCase();
        
        if (lower.contains("final:") || lower.contains("completed:") || 
            lower.contains("finished:") || lower.contains("task done")) {
            return AgentStepType.COMPLETED;
        }
        
        if (lower.contains("next step:") || lower.contains("execution:")) {
            return AgentStepType.ACTION;
        }
        
        if (lower.contains("planning:")) {
            return AgentStepType.PLANNING;
        }
        
        return AgentStepType.PLANNING;
    }

    @Override
    protected String extractContent(AiMessage aiMessage, AgentStepType stepType) {
        String response = aiMessage.text();
        if (stepType == AgentStepType.COMPLETED) {
            Matcher matcher = COMPLETED_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        if (stepType == AgentStepType.ACTION) {
            Matcher matcher = EXECUTION_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        if (stepType == AgentStepType.PLANNING) {
            Matcher matcher = PLAN_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        return response.length() > 500 ? response.substring(0, 500) : response;
    }

    @Override
    public void sendStepUpdate(AgentState state, AgentStep step) {
        if (websocketService != null && state.getSessionId() != null) {
            websocketService.sendToSession(state.getSessionId(), 
                WebSocketMessage.step(state.getTaskId(), step.getType(), 
                    step.getThought() != null ? step.getThought() : 
                    step.getAction() != null ? step.getAction() : 
                    step.getResult() != null ? step.getResult() : ""));
        }
    }
}

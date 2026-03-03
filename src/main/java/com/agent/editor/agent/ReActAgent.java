package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.WebSocketMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class ReActAgent extends BaseAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReActAgent.class);
    
    @Autowired
    private ChatModel chatLanguageModel;
    
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
        "(?:THOUGHT|REASONING):\\s*(.*?)(?:ACTION|$)", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern ACTION_PATTERN_LOCAL = Pattern.compile(
        "ACTION:\\s*(\\w+)\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern RESULT_PATTERN = Pattern.compile(
        "(?:RESULT|FINAL):\\s*(.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public AgentMode getMode() {
        return AgentMode.REACT;
    }

    @Override
    protected List<ToolSpecification> buildTools() {
        return Arrays.asList(
//            ToolSpecification.builder()
//                .name("readDocument")
//                .description("Read the current document content")
//                .build(),
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
    protected String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI-powered document editing agent using the ReAct (Reasoning + Acting) pattern.\n");
        prompt.append("Your goal is to understand user instructions and edit the document accordingly.\n\n");
        
        prompt.append("## ReAct Pattern\n");
        prompt.append("Think step by step:\n");
        prompt.append("1. Analyze the user's instruction\n");
        prompt.append("2. Take ONE action at a time using the available tools\n");
        prompt.append("3. Observe the result\n");
        prompt.append("4. Continue or complete based on the result\n");
        prompt.append("5. Complete the task by answering with 'done' or calling terminateTask function when finished\n\n");
        
        logger.info("【ReAct系统Prompt构建完成】, 长度: {}", prompt.length());
        
        return prompt.toString();
    }

    @Override
    protected AgentStepType parseResponse(AiMessage aiMessage, AgentState state) {
        if (!aiMessage.hasToolExecutionRequests()) {
            String lower = aiMessage.text().toLowerCase();
            if (lower.contains("final:") || lower.contains("task complete") || lower.contains("done")) {
                return AgentStepType.COMPLETED;
            }
            return AgentStepType.THINKING;
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            for (ToolExecutionRequest request : toolExecutionRequests) {
                if ("terminateTask".equalsIgnoreCase(request.name())) {
                    return AgentStepType.COMPLETED;
                }
            }
            return AgentStepType.ACTION;
        }
    }

    @Override
    protected String extractContent(AiMessage aiMessage, AgentStepType stepType) {
        if (stepType == AgentStepType.ACTION) {
            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            StringBuilder actionContent = new StringBuilder();
            for (ToolExecutionRequest request : toolExecutionRequests) {
                actionContent.append("ACTION: ").append(request.name()).append("\n");
            }
            return actionContent.toString();
        }

        if (stepType == AgentStepType.THINKING) {
            return aiMessage.text();
        }
        
        if (stepType == AgentStepType.COMPLETED) {
            return "done";
        }
        
        return aiMessage.text();
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

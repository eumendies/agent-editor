package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.WebSocketMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
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
                .build()
        );
    }

    @Override
    protected String buildSystemPrompt(AgentState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI-powered document editing agent using the ReAct (Reasoning + Acting) pattern.\n");
        prompt.append("Your goal is to understand user instructions and edit the document accordingly.\n\n");
        
        prompt.append("## ReAct Pattern\n");
        prompt.append("Think step by step:\n");
        prompt.append("1. Analyze the user's instruction\n");
        prompt.append("2. Take ONE action (by function calling) at a time\n");
        prompt.append("3. Observe the result\n");
        prompt.append("4. Continue or complete based on the result\n\n");
        
        prompt.append("\n## Current Document:\n");
        prompt.append(state.getDocument().getContent());
        
        prompt.append("\n\n## User Instruction:\n");
        prompt.append(state.getInstruction());
        
        logger.info("【ReAct系统Prompt构建完成】, 长度: {}", prompt.length());
        
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
    protected String getInitialPrompt(AgentState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(buildSystemPrompt(state));
        prompt.append("\n\n");
        prompt.append("First, analyze the instruction and document.\n");
        prompt.append("Then provide your response in this format:\n\n");
        prompt.append("THOUGHT: Your reasoning about what to do\n");
        prompt.append("ACTION: tool_name(parameter)\n");
        prompt.append("RESULT: What you expect to happen (or the final document content if task is complete)\n");
        
        return prompt.toString();
    }

    @Override
    protected String getNextPrompt(AgentState state, AgentStep previousStep) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(buildSystemPrompt(state));
        prompt.append("\n\n");
        
        prompt.append("Previous step result:\n");
        prompt.append(previousStep.getResult());
        prompt.append("\n\n");
        
        prompt.append("Based on the above result, what's your next step?\n");
        prompt.append("If the task is complete, provide the final result.\n");
        prompt.append("Format:\n");
        prompt.append("THOUGHT: Your reasoning\n");
        prompt.append("ACTION: tool_name(parameter) OR FINAL: document_content\n");
        
        return prompt.toString();
    }

    @Override
    protected AgentStepType parseResponse(String response, AgentState state) {
        String lower = response.toLowerCase();
        
        if (lower.contains("final:") || lower.contains("task complete") || lower.contains("done")) {
            return AgentStepType.COMPLETED;
        }
        
        if (ACTION_PATTERN_LOCAL.matcher(response).find()) {
            return AgentStepType.ACTION;
        }
        
        return AgentStepType.THINKING;
    }

    @Override
    protected String extractContent(String response, AgentStepType stepType) {
        if (stepType == AgentStepType.ACTION) {
            Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(response);
            if (thoughtMatcher.find()) {
                return thoughtMatcher.group(1).trim();
            }
        }
        
        if (stepType == AgentStepType.COMPLETED) {
            Matcher resultMatcher = RESULT_PATTERN.matcher(response);
            if (resultMatcher.find()) {
                return resultMatcher.group(1).trim();
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

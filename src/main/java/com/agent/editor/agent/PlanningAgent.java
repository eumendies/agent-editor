package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.WebSocketMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private ChatLanguageModel chatLanguageModel;
    
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
    protected List<String> buildToolPrompt() {
        return Arrays.asList(
            "Available tools for planning agent:",
            "- read_document: Read the current document content",
            "- edit_document: Edit the document content with specified changes",
            "- search_content: Search for specific text in document",
            "- format_document: Format the document",
            "- analyze_document: Analyze document quality",
            "- undo_change: Undo last change",
            "- preview_changes: Preview changes before applying",
            "- compare_versions: Compare document versions"
        );
    }

    @Override
    protected String buildSystemPrompt(AgentState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI-powered document editing agent using the Planning pattern.\n");
        prompt.append("Your goal is to create and execute a structured plan to fulfill user instructions.\n\n");
        
        prompt.append("## Planning Pattern\n");
        prompt.append("1. First, create a detailed execution plan (PLANNING phase)\n");
        prompt.append("2. Then execute each step one by one (EXECUTION phase)\n");
        prompt.append("3. Validate each step's result\n");
        prompt.append("4. When all steps are done, provide the final result (COMPLETED phase)\n\n");
        
        prompt.append("## Available Tools\n");
        for (String line : buildToolPrompt()) {
            prompt.append(line).append("\n");
        }
        
        prompt.append("\n## Important Rules\n");
        prompt.append("- Create a clear plan first, then execute step by step\n");
        prompt.append("- Execute only ONE step at a time\n");
        prompt.append("- After each step, evaluate if it's complete before moving to the next\n");
        
        prompt.append("\n## Current Document:\n");
        prompt.append(state.getDocument().getContent());
        
        prompt.append("\n\n## User Instruction:\n");
        prompt.append(state.getInstruction());
        
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
    protected String getInitialPrompt(AgentState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(buildSystemPrompt(state));
        prompt.append("\n\n");
        prompt.append("First, analyze the instruction and create a detailed execution plan.\n");
        prompt.append("Then start executing the first step.\n\n");
        prompt.append("Format your response as:\n\n");
        prompt.append("PLANNING: 1. First step 2. Second step 3. Third step ...\n");
        prompt.append("EXECUTION: First step action\n");
        
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
        
        prompt.append("Based on the above result:\n");
        prompt.append("- If the task is complete, provide FINAL: document_content\n");
        prompt.append("- If more steps are needed, provide: NEXT STEP: action\n");
        prompt.append("- You can also update the plan if needed\n\n");
        
        return prompt.toString();
    }

    @Override
    protected AgentStepType parseResponse(String response, AgentState state) {
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
    protected String extractContent(String response, AgentStepType stepType) {
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

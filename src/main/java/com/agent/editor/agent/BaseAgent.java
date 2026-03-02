package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.*;
import com.agent.editor.websocket.WebSocketService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseAgent implements AgentExecutor {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Autowired
    protected ChatModel chatLanguageModel;
    
    @Autowired
    protected WebSocketService websocketService;
    
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "ACTION:\\s*(\\w+)\\((.*?)\\)", Pattern.CASE_INSENSITIVE
    );

    public abstract AgentMode getMode();
    
    protected abstract List<ToolSpecification> buildTools();
    
    protected abstract String buildSystemPrompt(AgentState state);
    
    protected abstract AgentStep createStep(AgentState state, AgentStepType type, 
                                             String content, Map<String, Object> metadata);
    
    protected abstract String getInitialPrompt(AgentState state);
    
    protected abstract String getNextPrompt(AgentState state, AgentStep previousStep, 
                                           Map<String, Object> previousMetadata);

    protected abstract String extractContent(String response, AgentStepType stepType);
    
    protected abstract AgentStepType parseResponse(String response, AgentState state);

    public abstract void sendStepUpdate(AgentState state, AgentStep step);

    @Override
    public AgentState execute(Document document, String instruction, String sessionId, 
                              AgentMode mode, Integer maxSteps) {
        logger.info("========================================");
        logger.info("Agent执行开始 - Mode: {}", mode);
        logger.info("========================================");
        logger.info("用户输入 instruction: {}", instruction);
        logger.info("文档ID: {}, 文档标题: {}", document.getId(), document.getTitle());
        logger.info("原始文档内容:\n{}", document.getContent());
        logger.info("========================================");
        
        AgentState state = new AgentState(document, instruction, mode);
        state.setSessionId(sessionId);
        state.setStatus("RUNNING");
        
        if (maxSteps != null) {
            state.setMaxSteps(maxSteps);
        }
        
        state.setStartTime(System.currentTimeMillis());
        
        try {
            executeLoop(state);
            
            if (state.isCompleted()) {
                state.setStatus("COMPLETED");
                logger.info("Agent执行完成!");
                logger.info("最终文档内容:\n{}", state.getDocument().getContent());
            } else {
                state.setStatus("PARTIAL");
                logger.warn("Agent执行未完成, 当前步骤: {}", state.getCurrentStep());
            }
            
        } catch (Exception e) {
            logger.error("Agent执行出错", e);
            state.setStatus("ERROR");
        }
        
        state.setEndTime(System.currentTimeMillis());
        long duration = state.getEndTime() - state.getStartTime();
        logger.info("========================================");
        logger.info("Agent执行结束 - 耗时: {}ms, 状态: {}", duration, state.getStatus());
        logger.info("========================================");
        
        return state;
    }

    protected void executeLoop(AgentState state) {
        AgentStep previousStep = null;
        
        while (state.getCurrentStep() < state.getMaxSteps() && !state.isCompleted()) {
            state.incrementStep();
            
            logger.info("");
            logger.info("═══════════════════════════════════════════");
            logger.info("步骤 {}/{} - {}", state.getCurrentStep(), state.getMaxSteps(), state.getMode());
            logger.info("═══════════════════════════════════════════");
            
            try {
                String prompt;
                if (state.getCurrentStep() == 1) {
                    prompt = getInitialPrompt(state);
                } else {
                    prompt = getNextPrompt(state, previousStep, previousStep.getMetadata());
                }
                
                logger.info("【构造的Prompt】:\n{}", prompt);
                logger.info("───────────────────────────────────────");

                List<ToolSpecification> tools = buildTools();
                
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(UserMessage.from(prompt))
                        .toolSpecifications(tools)
                        .build();
                
                ChatResponse aiMessageResponse = chatLanguageModel.chat(chatRequest);
                AiMessage aiMessage = aiMessageResponse.aiMessage();
                String response;
                
                if (aiMessage.hasToolExecutionRequests()) {
                    logger.info("【AI请求执行工具】");
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                    
                    for (ToolExecutionRequest toolRequest : toolRequests) {
                        logger.info("  - 工具: {}, 参数: {}", toolRequest.name(), toolRequest.arguments());
                    }
                    
                    String toolResult = executeToolByName(toolRequests.get(0), state.getDocument());
                    logger.info("【工具执行结果】: {}", toolResult);
                    
                    response = toolResult;
                } else {
                    response = aiMessage.text();
                }
                
                logger.info("【AI输出】:\n{}", response);
                logger.info("───────────────────────────────────────");
                
                AgentStepType stepType = parseResponse(response, state);
                logger.info("【解析的步骤类型】: {}", stepType);
                
                String content = extractContent(response, stepType);
                logger.info("【提取的内容】: {}", content != null ? (content.length() > 100 ? content.substring(0, 100) + "..." : content) : "null");
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("rawResponse", response);
                
                if (aiMessage.hasToolExecutionRequests()) {
                    stepType = AgentStepType.OBSERVATION;
                    if (response.equals("done")) {
                        stepType = AgentStepType.COMPLETED;
                    }
                    ToolExecutionRequest toolRequest = aiMessage.toolExecutionRequests().get(0);
                    metadata.put("toolName", toolRequest.name());
                    metadata.put("toolArguments", toolRequest.arguments());
                    metadata.put("toolResult", response);
                }
                
                AgentStep step = createStep(state, stepType, content, metadata);
                state.addStep(step);
                
                sendStepUpdate(state, step);
                
                if (stepType == AgentStepType.COMPLETED || stepType == AgentStepType.RESULT) {
//                    if (content != null && content.length() > 10) {
//                        logger.info("【文档更新】: 即将更新文档内容");
//                        state.getDocument().setContent(content);
//                        logger.info("【文档已更新】:\n{}", content);
//                    }
                    state.setCompleted(true);
                    sendStepUpdate(state, step);
                    break;
                }
                
                previousStep = step;
                
            } catch (Exception e) {
                logger.error("【步骤执行错误】 at step {}", state.getCurrentStep(), e);
                AgentStep errorStep = createStep(state, AgentStepType.ERROR, 
                    "Error: " + e.getMessage(), new HashMap<>());
                state.addStep(errorStep);
                sendStepUpdate(state, errorStep);
                break;
            }
        }
    }
    
    protected String executeToolByName(ToolExecutionRequest toolRequest, Document document) {
        String toolName = toolRequest.name();
        String arguments = toolRequest.arguments();
        
        logger.info("执行工具: {}, 参数: {}", toolName, arguments);
        
        try {
            Map<String, Object> params = parseJsonArguments(arguments);
            
            switch (toolName) {
                case "readDocument":
                    return document.getContent();
                    
                case "editDocument":
                    Object contentObj = params.get("content");
                    if (contentObj != null) {
                        String newContent = contentObj.toString();
                        document.setContent(newContent);
                        return "Document edited successfully.\n\n" + newContent;
                    }
                    return "No content provided";
                    
                case "searchContent":
                    Object patternObj = params.get("pattern");
                    if (patternObj != null && document.getContent() != null) {
                        String pattern = patternObj.toString();
                        boolean found = document.getContent().toLowerCase().contains(pattern.toLowerCase());
                        return "Search for '" + pattern + "': " + (found ? "Found" : "Not found");
                    }
                    return "Search completed";
                    
                case "formatDocument":
                    if (document.getContent() != null) {
                        StringBuilder formatted = new StringBuilder();
                        for (String line : document.getContent().split("\n")) {
                            formatted.append("  ").append(line).append("\n");
                        }
                        document.setContent(formatted.toString().trim());
                        return "Document formatted:\n" + document.getContent();
                    }
                    return "No content to format";
                    
                case "analyzeDocument":
                    String content = document.getContent();
                    int words = content != null ? content.split("\\s+").length : 0;
                    int lines = content != null ? content.split("\n").length : 0;
                    return "Words: " + words + ", Lines: " + lines + ", Chars: " + (content != null ? content.length() : 0);
                    
                case "undoChange":
                    return "Undo completed";
                    
                case "previewChanges":
                    Object previewContent = params.get("content");
                    return "Preview:\n" + (previewContent != null ? previewContent.toString() : "No content");
                    
                case "compareVersions":
                    return "Current version (" + document.getContent().length() + " chars):\n" + document.getContent();

                case "terminateTask":
                    return "done";
                    
                default:
                    return "Unknown tool: " + toolName;
            }
        } catch (Exception e) {
            logger.error("Error executing tool: {}", toolName, e);
            return "Error: " + e.getMessage();
        }
    }
    
    private Map<String, Object> parseJsonArguments(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        
        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }
        
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "").replace("'", "");
                String value = keyValue[1].trim().replace("\"", "").replace("'", "");
                result.put(key, value);
            }
        }
        return result;
    }
}

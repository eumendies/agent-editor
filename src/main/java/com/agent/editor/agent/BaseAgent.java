package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.dto.*;
import com.agent.editor.websocket.WebSocketService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

public abstract class BaseAgent implements AgentExecutor {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    protected ChatModel chatLanguageModel;
    
    @Autowired
    protected WebSocketService websocketService;
    
    private ChatMemory chatMemory;

    private AgentState state;

    public abstract AgentMode getMode();
    
    protected abstract List<ToolSpecification> buildTools();
    
    protected abstract String buildSystemPrompt();
    
    protected abstract AgentStep createStep(AgentState state, AgentStepType type, 
                                             String content, Map<String, Object> metadata);

    protected abstract String extractContent(AiMessage aiMessage, AgentStepType stepType);
    
    protected abstract AgentStepType parseResponse(AiMessage aiMessage, AgentState state);

    public abstract void sendStepUpdate(AgentState state, AgentStep step);
    
    protected String buildUserMessage(Document document, String instruction) {
        return "Current document content:\n" +
                "---BEGIN---\n" +
                document.getContent() +
                "\n---END---\n\n" +
                "User instruction: " + instruction;
    }

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
        
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        String systemPrompt = buildSystemPrompt();
        chatMemory.add(SystemMessage.from(systemPrompt));
        
        String userMessage = buildUserMessage(document, instruction);
        chatMemory.add(UserMessage.from(userMessage));
        
        this.state = new AgentState(document, instruction, mode);
        state.setSessionId(sessionId);
        state.setStatus("RUNNING");
        
        if (maxSteps != null) {
            state.setMaxSteps(maxSteps);
        }
        
        state.setStartTime(System.currentTimeMillis());
        
        try {
            executeLoop();
            
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

    protected void executeLoop() {
        while (state.getCurrentStep() < state.getMaxSteps() && !state.isCompleted()) {
            state.incrementStep();
            logger.info("");
            logger.info("═══════════════════════════════════════════");
            logger.info("步骤 {}/{} - {}", state.getCurrentStep(), state.getMaxSteps(), state.getMode());
            logger.info("═══════════════════════════════════════════");
            
            try {
                List<ToolSpecification> tools = buildTools();
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(chatMemory.messages())
                        .toolSpecifications(tools)
                        .build();

                ChatResponse aiMessageResponse = chatLanguageModel.chat(chatRequest);
                AiMessage aiMessage = aiMessageResponse.aiMessage();
                chatMemory.add(aiMessage);
                
                String response = aiMessage.text();
                logger.info("【AI原始响应】: {}\n", response);
                
                if (aiMessage.hasToolExecutionRequests()) {
                    logger.info("【AI请求执行工具】");
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                    
                    for (ToolExecutionRequest toolRequest : toolRequests) {
                        logger.info("  - 工具: {}, 参数: {}", toolRequest.name(), toolRequest.arguments());
                        String toolResult = executeToolByName(toolRequest, state.getDocument());
                        logger.info("【工具执行结果】: {}", toolResult);

                        ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(toolRequest, toolResult);
                        chatMemory.add(toolResultMessage);
                    }
                }
                
                AgentStepType stepType = parseResponse(aiMessage, state);
                logger.info("【解析的步骤类型】: {}", stepType);
                
                String content = extractContent(aiMessage, stepType);
                logger.info("【提取的内容】: {}", content != null ? (content.length() > 100 ? content.substring(0, 100) + "..." : content) : "null");
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("rawResponse", response);
                
                AgentStep step = createStep(state, stepType, content, metadata);
                state.addStep(step);
                
                sendStepUpdate(state, step);
                
                if (stepType == AgentStepType.COMPLETED || stepType == AgentStepType.RESULT) {
                    state.setCompleted(true);
                    break;
                }

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
                    
                case "respondToUser":
                    Object messageObj = params.get("message");
                    if (messageObj != null) {
                        AgentStep userStep = createStep(state, AgentStepType.USER_MESSAGE, messageObj.toString(), new HashMap<>());
                        state.addStep(userStep);
                        sendStepUpdate(state, userStep);
                        return "RESPOND_TO_USER SUCCESS";
                    } else {
                        return "RESPOND_TO_USER: No message provided";
                    }
                default:
                    return "Unknown tool: " + toolName;
            }
        } catch (Exception e) {
            logger.error("Error executing tool: {}", toolName, e);
            return "Error: " + e.getMessage();
        }
    }
    
    private Map<String, Object> parseJsonArguments(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.warn("Failed to parse JSON arguments: {}", json, e);
            return new HashMap<>();
        }
    }
}

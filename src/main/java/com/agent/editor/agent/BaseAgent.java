package com.agent.editor.agent;

import com.agent.editor.model.*;
import com.agent.editor.websocket.WebSocketService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@SuperBuilder
public abstract class BaseAgent implements AgentExecutor {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected ChatModel chatLanguageModel;

    protected WebSocketService websocketService;
    
    private ChatMemory chatMemory;

    private AgentState state;

    private EditorAgentTools tools;

    public abstract AgentMode getMode();
    
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

    private void initAgent(Document document, String instruction, String sessionId, AgentMode mode, Integer maxSteps) {
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

        this.tools = new EditorAgentTools(state, websocketService);
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
        
        initAgent(document, instruction, sessionId, mode, maxSteps);
        
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
                List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(EditorAgentTools.class);
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(chatMemory.messages())
                        .toolSpecifications(toolSpecifications)
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
                        String toolResult = tools.executeTool(toolRequest);
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
}

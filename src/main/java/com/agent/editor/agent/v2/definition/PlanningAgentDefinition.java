package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.orchestration.PlanResult;
import com.agent.editor.agent.v2.orchestration.PlanStep;
import com.agent.editor.agent.v2.core.runtime.ExecutionContext;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning agent 只负责把原始任务拆成结构化步骤，不直接执行工具。
 */
public class PlanningAgentDefinition implements AgentDefinition {

    private final ChatModel chatModel;

    public PlanningAgentDefinition(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public AgentType type() {
        return AgentType.PLANNING;
    }

    @Override
    public Decision decide(ExecutionContext context) {
        // 兼容统一 runtime：当 planner 被当作普通 agent 运行时，返回可展示的计划文本。
        PlanResult plan = createPlan(context.request().document(), context.request().instruction());
        String result = plan.steps().stream()
                .map(step -> step.order() + ". " + step.instruction())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(context.request().instruction());
        return new Decision.Complete(result, "planning complete");
    }

    public PlanResult createPlan(DocumentSnapshot document, String instruction) {
        // 没有模型时退化成单步计划，保证 orchestration 仍然可跑通。
        if (chatModel == null) {
            return fallbackPlan(instruction);
        }

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(buildSystemPrompt()),
                        UserMessage.from(buildUserPrompt(document, instruction))
                ))
                .build());

        return parsePlan(response.aiMessage(), instruction);
    }

    private String buildSystemPrompt() {
        return """
                You are a planning agent for a document editor.
                Break the user request into a short numbered list of execution steps.
                Return one step per line.
                """;
    }

    private String buildUserPrompt(DocumentSnapshot document, String instruction) {
        return """
                Document:
                %s

                Instruction:
                %s
                """.formatted(document.content(), instruction);
    }

    private PlanResult parsePlan(AiMessage aiMessage, String fallbackInstruction) {
        List<PlanStep> steps = new ArrayList<>();
        String[] lines = aiMessage.text().split("\\R");
        int nextOrder = 1;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 优先解析 "1. xxx" / "1) xxx" 这类显式编号，没有编号时按出现顺序补序号。
            int separator = findSeparatorIndex(line);
            if (separator > 0 && isNumeric(line.substring(0, separator))) {
                int order = Integer.parseInt(line.substring(0, separator));
                String instruction = line.substring(separator + 1).trim();
                if (!instruction.isEmpty()) {
                    steps.add(new PlanStep(order, instruction));
                    nextOrder = Math.max(nextOrder, order + 1);
                }
                continue;
            }

            steps.add(new PlanStep(nextOrder++, line));
        }

        if (steps.isEmpty()) {
            return fallbackPlan(fallbackInstruction);
        }
        return new PlanResult(steps);
    }

    private int findSeparatorIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '.' || current == ')' || current == '-') {
                return i;
            }
            if (!Character.isDigit(current)) {
                return -1;
            }
        }
        return -1;
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private PlanResult fallbackPlan(String instruction) {
        return new PlanResult(List.of(new PlanStep(1, instruction)));
    }
}

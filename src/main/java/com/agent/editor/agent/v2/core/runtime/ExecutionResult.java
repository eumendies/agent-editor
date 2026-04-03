package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * runtime 对外返回的统一结果对象，封装结构化产物、文本结果与最终上下文。
 *
 * @param <T> agent 输出的结构化结果类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult<T> {

    // Agent 执行后返回的结构化结果。
    private T result;
    // 面向调用方的最终消息。
    private String finalMessage;
    // 最终生成或更新后的正文内容。
    private String finalContent;
    // 执行结束时保留下来的运行态快照。
    private AgentRunContext finalState;

    public ExecutionResult(String finalMessage) {
        this(null, finalMessage, finalMessage, new AgentRunContext(
                null,
                0,
                finalMessage,
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }

    public ExecutionResult(String finalMessage, String finalContent) {
        this(null,  finalMessage, finalContent, new AgentRunContext(
                null,
                0,
                finalContent,
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }

    public ExecutionResult(T result, String finalMessage, String finalContent) {
        this(result,  finalMessage, finalContent, new AgentRunContext(
                null,
                0,
                finalContent,
                new ChatTranscriptMemory(List.of()),
                ExecutionStage.RUNNING,
                null,
                List.of()
        ));
    }
}

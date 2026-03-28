package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.ExecutionStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult<T> {

    private T result;   // Agent执行后返回的结构化结果
    private String finalMessage;
    private String finalContent;
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

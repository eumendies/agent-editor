package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.state.ExecutionState;

public record ExecutionContext(ExecutionRequest request, ExecutionState state) {
}

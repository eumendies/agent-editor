package com.agent.editor.agent.v2.core.runtime;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

import com.agent.editor.agent.v2.core.state.ExecutionState;

public record ExecutionContext(ExecutionRequest request, ExecutionState state, List<ToolSpecification> toolSpecifications) {
}

package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.ToolLoopDecision;

public interface TerminationPolicy {
    boolean shouldTerminate(ToolLoopDecision toolLoopDecision, ExecutionStateSnapshot snapshot);
}

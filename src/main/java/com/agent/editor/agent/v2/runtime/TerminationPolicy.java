package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.core.agent.Decision;

public interface TerminationPolicy {
    boolean shouldTerminate(Decision decision, ExecutionStateSnapshot snapshot);
}

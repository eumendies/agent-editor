package com.agent.editor.agent.v2.trace;

import java.util.List;

public interface TraceStore {
    void append(TraceRecord record);

    List<TraceRecord> getByTaskId(String taskId);
}

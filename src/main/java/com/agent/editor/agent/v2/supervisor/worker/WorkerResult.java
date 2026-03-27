package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.state.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResult {

    private String workerId;
    private TaskStatus status;
    private String summary;
    private String updatedContent;
}

package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.supervisor.worker.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.worker.WorkerResult;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SupervisorContext {

    private String taskId;
    private String sessionId;
    private String originalInstruction;
    private String currentContent;
    private List<WorkerDefinition> availableWorkers = List.of();
    private List<WorkerResult> workerResults = List.of();

    public SupervisorContext(String taskId,
                             String sessionId,
                             String originalInstruction,
                             String currentContent,
                             List<WorkerDefinition> availableWorkers,
                             List<WorkerResult> workerResults) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.originalInstruction = originalInstruction;
        this.currentContent = currentContent;
        setAvailableWorkers(availableWorkers);
        setWorkerResults(workerResults);
    }

    public void setAvailableWorkers(List<WorkerDefinition> availableWorkers) {
        this.availableWorkers = availableWorkers == null ? List.of() : List.copyOf(availableWorkers);
    }

    public void setWorkerResults(List<WorkerResult> workerResults) {
        this.workerResults = workerResults == null ? List.of() : List.copyOf(workerResults);
    }
}

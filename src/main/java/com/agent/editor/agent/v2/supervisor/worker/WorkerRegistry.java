package com.agent.editor.agent.v2.supervisor.worker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkerRegistry {

    private final Map<String, WorkerDefinition> workers = new LinkedHashMap<>();

    public void register(WorkerDefinition workerDefinition) {
        workers.put(workerDefinition.workerId(), workerDefinition);
    }

    public WorkerDefinition get(String workerId) {
        return workers.get(workerId);
    }

    public List<WorkerDefinition> all() {
        return new ArrayList<>(workers.values());
    }
}

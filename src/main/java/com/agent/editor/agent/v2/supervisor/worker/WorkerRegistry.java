package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.context.SupervisorContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkerRegistry {

    private final Map<String, SupervisorContext.WorkerDefinition> workers = new LinkedHashMap<>();

    public void register(SupervisorContext.WorkerDefinition workerDefinition) {
        workers.put(workerDefinition.getWorkerId(), workerDefinition);
    }

    public SupervisorContext.WorkerDefinition get(String workerId) {
        return workers.get(workerId);
    }

    public List<SupervisorContext.WorkerDefinition> all() {
        return new ArrayList<>(workers.values());
    }
}

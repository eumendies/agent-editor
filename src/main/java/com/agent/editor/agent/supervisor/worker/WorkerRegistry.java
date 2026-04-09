package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.agent.core.context.SupervisorContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * supervisor 可调度 worker 的注册表。
 */
public class WorkerRegistry {

    // 使用 LinkedHashMap 保留注册顺序，规则回退时“第一个候选”具有稳定且可预测的含义。
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

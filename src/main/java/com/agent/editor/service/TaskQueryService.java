package com.agent.editor.service;

import com.agent.editor.agent.v2.state.TaskState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskQueryService {

    private final Map<String, TaskState> taskStates = new ConcurrentHashMap<>();

    public void save(TaskState state) {
        taskStates.put(state.taskId(), state);
    }

    public TaskState findById(String taskId) {
        return taskStates.get(taskId);
    }
}

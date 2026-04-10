package com.agent.editor.service;

import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.agent.core.state.TaskState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskQueryService {

    private final Map<String, TaskState> taskStates = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> taskEvents = new ConcurrentHashMap<>();

    public void save(TaskState state) {
        taskStates.put(state.getTaskId(), state);
    }

    public TaskState findById(String taskId) {
        return taskStates.get(taskId);
    }

    public void remove(String taskId) {
        taskStates.remove(taskId);
        taskEvents.remove(taskId);
    }

    public void appendEvent(ExecutionEvent event) {
        taskEvents.computeIfAbsent(event.getTaskId(), ignored -> new ArrayList<>()).add(event);
    }

    public List<ExecutionEvent> getEvents(String taskId) {
        return taskEvents.getOrDefault(taskId, Collections.emptyList());
    }
}

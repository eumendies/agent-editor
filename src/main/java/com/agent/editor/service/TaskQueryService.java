package com.agent.editor.service;

import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.core.state.TaskState;
import com.agent.editor.model.AgentStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskQueryService {

    private final com.agent.editor.agent.v2.event.LegacyEventAdapter legacyEventAdapter = new com.agent.editor.agent.v2.event.LegacyEventAdapter();
    private final Map<String, TaskState> taskStates = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> taskEvents = new ConcurrentHashMap<>();

    public void save(TaskState state) {
        taskStates.put(state.taskId(), state);
    }

    public TaskState findById(String taskId) {
        return taskStates.get(taskId);
    }

    public void appendEvent(ExecutionEvent event) {
        taskEvents.computeIfAbsent(event.taskId(), ignored -> new ArrayList<>()).add(event);
    }

    public List<ExecutionEvent> getEvents(String taskId) {
        return taskEvents.getOrDefault(taskId, Collections.emptyList());
    }

    public List<AgentStep> getTaskSteps(String taskId) {
        List<ExecutionEvent> events = getEvents(taskId);
        List<AgentStep> steps = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            steps.add(legacyEventAdapter.toStep(events.get(i), i + 1));
        }
        return steps;
    }
}

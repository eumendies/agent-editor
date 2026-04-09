package com.agent.editor.service;

import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.agent.core.state.TaskState;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentStepType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public List<AgentStep> getTaskSteps(String taskId) {
        List<ExecutionEvent> events = getEvents(taskId);
        List<AgentStep> steps = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            steps.add(toLegacyStep(events.get(i), i + 1));
        }
        return steps;
    }

    private AgentStep toLegacyStep(ExecutionEvent event, int stepNumber) {
        AgentStepType stepType = toLegacyStepType(event);
        AgentStep step = new AgentStep(UUID.randomUUID().toString(), event.getTaskId(), stepNumber, stepType);

        switch (stepType) {
            case ACTION -> step.setAction(event.getMessage());
            case OBSERVATION -> step.setObservation(event.getMessage());
            case RESULT, COMPLETED -> {
                step.setResult(event.getMessage());
                step.setFinal(stepType == AgentStepType.COMPLETED);
            }
            case ERROR -> step.setError(event.getMessage());
            default -> step.setThought(event.getMessage());
        }

        return step;
    }

    private AgentStepType toLegacyStepType(ExecutionEvent event) {
        return switch (event.getType()) {
            case TOOL_CALLED, WORKER_SELECTED -> AgentStepType.ACTION;
            case TOOL_SUCCEEDED, WORKER_COMPLETED -> AgentStepType.OBSERVATION;
            case TASK_COMPLETED, SUPERVISOR_COMPLETED -> AgentStepType.COMPLETED;
            case TASK_FAILED, TOOL_FAILED -> AgentStepType.ERROR;
            default -> AgentStepType.THINKING;
        };
    }
}

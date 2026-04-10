package com.agent.editor.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentTaskResponseTest {

    @Test
    void shouldNotExposeLegacyCurrentStepField() {
        boolean hasCurrentStepField = Arrays.stream(AgentTaskResponse.class.getDeclaredFields())
                .anyMatch(field -> "currentStep".equals(field.getName()));

        assertFalse(hasCurrentStepField);
    }
}

package com.agent.editor.service;

import com.agent.editor.agent.v2.core.memory.LongTermMemoryItem;
import com.agent.editor.agent.v2.core.memory.LongTermMemoryType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfilePromptAssemblerTest {

    @Test
    void shouldAssembleConfirmedProfilesIntoCompactPromptBlock() {
        UserProfilePromptAssembler assembler = new UserProfilePromptAssembler();

        String guidance = assembler.assemble(List.of(
                profile("Always answer in Chinese"),
                profile("Prefer concise edits")
        ));

        assertTrue(guidance.contains("Confirmed user profile"));
        assertTrue(guidance.contains("- Always answer in Chinese"));
        assertTrue(guidance.contains("- Prefer concise edits"));
    }

    @Test
    void shouldReturnEmptyGuidanceWhenNoConfirmedProfilesExist() {
        UserProfilePromptAssembler assembler = new UserProfilePromptAssembler();

        assertEquals("", assembler.assemble(List.of()));
    }

    private LongTermMemoryItem profile(String summary) {
        return new LongTermMemoryItem(
                "memory-" + summary.hashCode(),
                LongTermMemoryType.USER_PROFILE,
                null,
                summary,
                summary,
                "task-1",
                "session-1",
                List.of("confirmed"),
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 10, 5),
                new float[]{0.1f, 0.2f}
        );
    }
}

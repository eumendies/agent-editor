package com.agent.editor.agent.tool.memory;

import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryToolAccessPolicyTest {

    @Test
    void shouldExposeReadWriteMemoryToolsForMainWriteRole() {
        MemoryToolAccessPolicy policy = new MemoryToolAccessPolicy();

        assertEquals(
                List.of(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
                policy.allowedTools(ExecutionToolAccessRole.MAIN_WRITE)
        );
    }

    @Test
    void shouldExposeReadWriteMemoryToolsForMemoryRole() {
        MemoryToolAccessPolicy policy = new MemoryToolAccessPolicy();

        assertEquals(
                List.of(MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
                policy.allowedTools(Enum.valueOf(ExecutionToolAccessRole.class, "MEMORY"))
        );
    }

    @Test
    void shouldExposeSearchMemoryToolsForReviewRole() {
        MemoryToolAccessPolicy policy = new MemoryToolAccessPolicy();

        assertEquals(List.of(MemoryToolNames.SEARCH_MEMORY), policy.allowedTools(ExecutionToolAccessRole.REVIEW));
    }

    @Test
    void shouldHideMemoryToolsFromResearchRole() {
        MemoryToolAccessPolicy policy = new MemoryToolAccessPolicy();

        assertEquals(List.of(), policy.allowedTools(ExecutionToolAccessRole.RESEARCH));
    }
}

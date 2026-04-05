package com.agent.editor.agent.v2.tool.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainAgentMemoryToolAccessTest {

    @Test
    void shouldAppendMemoryToolsUsingCamelCaseNames() {
        List<String> tools = MainAgentMemoryToolAccess.append(List.of("searchContent"));

        assertEquals(
                List.of("searchContent", MemoryToolNames.SEARCH_MEMORY, MemoryToolNames.UPSERT_MEMORY),
                tools
        );
    }
}

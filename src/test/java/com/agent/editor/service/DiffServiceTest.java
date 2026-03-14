package com.agent.editor.service;

import com.agent.editor.dto.DiffResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffServiceTest {

    @Test
    void shouldGenerateAndStoreDiffHistory() {
        DiffService service = new DiffService();

        DiffResult result = service.recordDiff("doc-1", "old", "new");

        assertEquals("old", result.getOriginalContent());
        assertEquals("new", result.getModifiedContent());
        assertEquals(1, service.getDiffHistory("doc-1").size());
        assertTrue(result.getDiffHtml().contains("diff"));
    }
}

package com.agent.editor.agent.tool.external;

import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.tool.document.DocumentToolNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalToolAccessPolicyTest {

    private final ExternalToolAccessPolicy policy = new ExternalToolAccessPolicy();

    @Test
    void shouldExposeWebSearchForMainWriteRole() {
        assertEquals(List.of(DocumentToolNames.WEB_SEARCH), policy.allowedTools(ExecutionToolAccessRole.MAIN_WRITE));
    }

    @Test
    void shouldExposeWebSearchForResearchRole() {
        assertEquals(List.of(DocumentToolNames.WEB_SEARCH), policy.allowedTools(ExecutionToolAccessRole.RESEARCH));
    }

    @Test
    void shouldHideWebSearchForReviewAndMemoryRoles() {
        assertEquals(List.of(), policy.allowedTools(ExecutionToolAccessRole.REVIEW));
        assertEquals(List.of(), policy.allowedTools(ExecutionToolAccessRole.MEMORY));
    }
}

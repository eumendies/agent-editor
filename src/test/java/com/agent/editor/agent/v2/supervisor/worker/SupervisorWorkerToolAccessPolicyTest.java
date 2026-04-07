package com.agent.editor.agent.v2.supervisor.worker;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolAccessPolicy;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import com.agent.editor.agent.v2.tool.memory.MemoryToolAccessPolicy;
import com.agent.editor.agent.v2.tool.memory.MemoryToolNames;
import com.agent.editor.config.DocumentToolModeProperties;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupervisorWorkerToolAccessPolicyTest {

    @Test
    void shouldResolveWriterToolsFromExplicitExecutionRole() {
        SupervisorWorkerToolAccessPolicy policy = policy(10);
        SupervisorContext.WorkerDefinition worker = new SupervisorContext.WorkerDefinition(
                "writer",
                "Writer",
                "Apply edits",
                stubAgent(),
                List.of(),
                List.of("write", "edit"),
                ExecutionToolAccessRole.MAIN_WRITE
        );

        SupervisorWorkerToolAccessPolicy.WorkerToolAccess access = policy.resolve(
                worker,
                new DocumentSnapshot("doc-1", "Title", "x".repeat(80))
        );

        assertEquals(DocumentToolMode.INCREMENTAL, access.getDocumentToolMode());
        assertEquals(List.of(
                DocumentToolNames.READ_DOCUMENT_NODE,
                DocumentToolNames.PATCH_DOCUMENT_NODE,
                DocumentToolNames.SEARCH_CONTENT,
                MemoryToolNames.SEARCH_MEMORY
        ), access.getAllowedTools());
    }

    @Test
    void shouldResolveMemoryWorkerToolsFromExplicitExecutionRole() {
        SupervisorWorkerToolAccessPolicy policy = policy(10);
        SupervisorContext.WorkerDefinition worker = new SupervisorContext.WorkerDefinition(
                "memory",
                "Memory",
                "Maintain memory",
                stubAgent(),
                List.of(),
                List.of("memory"),
                ExecutionToolAccessRole.MEMORY
        );

        SupervisorWorkerToolAccessPolicy.WorkerToolAccess access = policy.resolve(
                worker,
                new DocumentSnapshot("doc-2", "Title", "x".repeat(80))
        );

        assertEquals(DocumentToolMode.FULL, access.getDocumentToolMode());
        assertEquals(List.of(
                MemoryToolNames.SEARCH_MEMORY,
                MemoryToolNames.UPSERT_MEMORY
        ), access.getAllowedTools());
    }

    @Test
    void shouldFallBackToWorkerDeclaredToolsWhenExecutionRoleIsAbsent() {
        SupervisorWorkerToolAccessPolicy policy = policy(10);
        SupervisorContext.WorkerDefinition worker = new SupervisorContext.WorkerDefinition(
                "custom",
                "Custom",
                "Custom worker",
                stubAgent(),
                List.of("customTool"),
                List.of("custom")
        );

        SupervisorWorkerToolAccessPolicy.WorkerToolAccess access = policy.resolve(
                worker,
                new DocumentSnapshot("doc-3", "Title", "x".repeat(80))
        );

        assertEquals(DocumentToolMode.FULL, access.getDocumentToolMode());
        assertEquals(List.of("customTool"), access.getAllowedTools());
    }

    private SupervisorWorkerToolAccessPolicy policy(int threshold) {
        DocumentToolAccessPolicy documentPolicy = new DocumentToolAccessPolicy(
                new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200),
                new DocumentToolModeProperties(threshold)
        );
        return new SupervisorWorkerToolAccessPolicy(
                documentPolicy,
                new ExecutionToolAccessPolicy(documentPolicy, new MemoryToolAccessPolicy())
        );
    }

    private Agent stubAgent() {
        return () -> AgentType.REACT;
    }
}

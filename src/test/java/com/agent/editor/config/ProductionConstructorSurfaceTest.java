package com.agent.editor.config;

import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.memory.ModelBasedMemoryCompressor;
import com.agent.editor.agent.planning.PlanningAgentContextFactory;
import com.agent.editor.agent.planning.PlanningAgentImpl;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorOrchestrator;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.GroundedWriterAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.MemoryAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.ResearcherAgentContextFactory;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.service.TaskApplicationService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionConstructorSurfaceTest {

    @Test
    void shouldExposeOnlyFinalConstructorsOnProductionBeans() {
        assertSinglePublicConstructor(
                SupervisorOrchestrator.class,
                List.of(
                        com.agent.editor.agent.core.agent.SupervisorAgent.class,
                        com.agent.editor.agent.core.runtime.SupervisorExecutionRuntime.class,
                        com.agent.editor.agent.supervisor.worker.WorkerRegistry.class,
                        com.agent.editor.agent.core.runtime.ExecutionRuntime.class,
                        com.agent.editor.agent.event.EventPublisher.class,
                        com.agent.editor.agent.supervisor.SupervisorContextFactory.class,
                        com.agent.editor.agent.tool.ExecutionToolAccessPolicy.class
                )
        );
        assertSinglePublicConstructor(
                TaskApplicationService.class,
                List.of(
                        com.agent.editor.service.DocumentService.class,
                        com.agent.editor.service.TaskQueryService.class,
                        com.agent.editor.service.DiffService.class,
                        com.agent.editor.service.PendingDocumentChangeService.class,
                        com.agent.editor.agent.task.TaskOrchestrator.class,
                        com.agent.editor.service.LongTermMemoryRetrievalService.class,
                        com.agent.editor.service.UserProfilePromptAssembler.class,
                        ObjectProvider.class,
                        ObjectProvider.class,
                        com.agent.editor.websocket.WebSocketService.class,
                        com.agent.editor.agent.event.EventPublisher.class,
                        TaskExecutor.class
                )
        );
        assertSinglePublicConstructor(
                ReactAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                PlanningAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class)
        );
        assertSinglePublicConstructor(
                ReflexionActorContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                ReflexionCriticContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                SupervisorContextFactory.class,
                List.of(com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                ResearcherAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class)
        );
        assertSinglePublicConstructor(
                MemoryAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class)
        );
        assertSinglePublicConstructor(
                GroundedWriterAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                EvidenceReviewerAgentContextFactory.class,
                List.of(ExecutionMemoryChatMessageMapper.class, com.agent.editor.agent.core.memory.MemoryCompressor.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                PlanningAgentImpl.class,
                List.of(com.agent.editor.agent.planning.PlanningAiService.class, StructuredDocumentService.class)
        );
        assertSinglePublicConstructor(
                ModelBasedMemoryCompressor.class,
                List.of(ChatModel.class, MemoryCompressionProperties.class)
        );
    }

    @Test
    void shouldNotExposeTestConvenienceConstructorsOnConfigurationProperties() {
        assertEquals(1, RagProperties.class.getConstructors().length);
        assertEquals(1, MemoryCompressionProperties.class.getConstructors().length);
        assertEquals(1, DocumentToolModeProperties.class.getConstructors().length);
    }

    private void assertSinglePublicConstructor(Class<?> type, List<Class<?>> parameterTypes) {
        Constructor<?>[] constructors = type.getConstructors();
        assertEquals(1, constructors.length, () -> type.getSimpleName() + " should expose exactly one public constructor");
        assertArrayEquals(
                parameterTypes.toArray(Class[]::new),
                Arrays.stream(constructors[0].getParameterTypes()).toArray(Class[]::new),
                () -> type.getSimpleName() + " should keep only the final production constructor"
        );
    }
}

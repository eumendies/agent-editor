package com.agent.editor.testsupport;

import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.planning.PlanningAgentContextFactory;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.GroundedWriterAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.MemoryAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.ResearcherAgentContextFactory;
import com.agent.editor.service.StructuredDocumentService;
import com.agent.editor.utils.rag.markdown.MarkdownSectionTreeBuilder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public final class AgentTestFixtures {

    private AgentTestFixtures() {
    }

    public static ExecutionMemoryChatMessageMapper memoryChatMessageMapper() {
        return new ExecutionMemoryChatMessageMapper();
    }

    public static StructuredDocumentService structuredDocumentService() {
        return new StructuredDocumentService(new MarkdownSectionTreeBuilder(), 4_000, 1_200);
    }

    public static ReactAgentContextFactory reactAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new ReactAgentContextFactory(memoryChatMessageMapper(), memoryCompressor, structuredDocumentService());
    }

    public static PlanningAgentContextFactory planningAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new PlanningAgentContextFactory(memoryChatMessageMapper(), memoryCompressor);
    }

    public static ReflexionActorContextFactory reflexionActorContextFactory(MemoryCompressor memoryCompressor) {
        return new ReflexionActorContextFactory(memoryChatMessageMapper(), memoryCompressor, structuredDocumentService());
    }

    public static ReflexionCriticContextFactory reflexionCriticContextFactory(MemoryCompressor memoryCompressor) {
        return new ReflexionCriticContextFactory(memoryChatMessageMapper(), memoryCompressor, structuredDocumentService());
    }

    public static SupervisorContextFactory supervisorContextFactory(MemoryCompressor memoryCompressor) {
        return new SupervisorContextFactory(memoryCompressor, structuredDocumentService());
    }

    public static ResearcherAgentContextFactory researcherAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new ResearcherAgentContextFactory(memoryChatMessageMapper(), memoryCompressor);
    }

    public static GroundedWriterAgentContextFactory groundedWriterAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new GroundedWriterAgentContextFactory(memoryChatMessageMapper(), memoryCompressor, structuredDocumentService());
    }

    public static EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new EvidenceReviewerAgentContextFactory(memoryChatMessageMapper(), memoryCompressor, structuredDocumentService());
    }

    public static MemoryAgentContextFactory memoryAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new MemoryAgentContextFactory(memoryChatMessageMapper(), memoryCompressor);
    }

    public static <T> ObjectProvider<T> singletonProvider(T bean) {
        return new SimpleObjectProvider<>(bean == null ? List.of() : List.of(bean));
    }

    public static <T> ObjectProvider<T> emptyProvider() {
        return new SimpleObjectProvider<>(List.of());
    }

    private static final class SimpleObjectProvider<T> implements ObjectProvider<T> {

        private final List<T> beans;

        private SimpleObjectProvider(List<T> beans) {
            this.beans = List.copyOf(beans);
        }

        @Override
        public T getObject(Object... args) {
            return getObject();
        }

        @Override
        public T getIfAvailable() {
            return beans.isEmpty() ? null : beans.get(0);
        }

        @Override
        public T getIfUnique() {
            return beans.size() == 1 ? beans.get(0) : null;
        }

        @Override
        public T getObject() {
            if (beans.isEmpty()) {
                throw new IllegalStateException("No bean available");
            }
            return beans.get(0);
        }

        @Override
        public Iterator<T> iterator() {
            return beans.iterator();
        }

        @Override
        public Stream<T> stream() {
            return beans.stream();
        }

        @Override
        public Stream<T> orderedStream() {
            return beans.stream();
        }
    }
}

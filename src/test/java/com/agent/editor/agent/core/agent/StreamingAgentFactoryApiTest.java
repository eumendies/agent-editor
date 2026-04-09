package com.agent.editor.agent.core.agent;

import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.react.ReactAgent;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.reflexion.ReflexionActor;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCritic;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgent;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.GroundedWriterAgent;
import com.agent.editor.agent.supervisor.worker.GroundedWriterAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.ResearcherAgent;
import com.agent.editor.agent.supervisor.worker.ResearcherAgentContextFactory;
import com.agent.editor.agent.support.NoOpMemoryCompressors;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class StreamingAgentFactoryApiTest {

    @Test
    void shouldExposeNamedFactoryMethodsInsteadOfPublicConstructors() {
        ChatModel chatModel = mock(ChatModel.class);
        StreamingLLMInvoker streamingLLMInvoker = mock(StreamingLLMInvoker.class);

        assertNotNull(ReactAgent.blocking(chatModel, new ReactAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ReactAgent.streaming(streamingLLMInvoker, new ReactAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ResearcherAgent.blocking(chatModel, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ResearcherAgent.streaming(streamingLLMInvoker, new ResearcherAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(GroundedWriterAgent.blocking(chatModel, new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(GroundedWriterAgent.streaming(streamingLLMInvoker, new GroundedWriterAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(EvidenceReviewerAgent.blocking(chatModel, new EvidenceReviewerAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(EvidenceReviewerAgent.streaming(streamingLLMInvoker, new EvidenceReviewerAgentContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ReflexionCritic.blocking(chatModel, new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ReflexionCritic.streaming(streamingLLMInvoker, new ReflexionCriticContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ReflexionActor.blocking(chatModel, new ReflexionActorContextFactory(NoOpMemoryCompressors.noop())));
        assertNotNull(ReflexionActor.streaming(streamingLLMInvoker, new ReflexionActorContextFactory(NoOpMemoryCompressors.noop())));

        assertEquals(0, ReactAgent.class.getConstructors().length);
        assertEquals(0, ResearcherAgent.class.getConstructors().length);
        assertEquals(0, GroundedWriterAgent.class.getConstructors().length);
        assertEquals(0, EvidenceReviewerAgent.class.getConstructors().length);
        assertEquals(0, ReflexionCritic.class.getConstructors().length);
        assertEquals(0, ReflexionActor.class.getConstructors().length);
    }
}

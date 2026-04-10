package com.agent.editor.config;

import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.context.SupervisorContext;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.mapper.ExecutionMemoryChatMessageMapper;
import com.agent.editor.agent.model.StreamingLLMInvoker;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.supervisor.routing.HybridSupervisorAgent;
import com.agent.editor.agent.supervisor.worker.*;
import com.agent.editor.agent.supervisor.worker.ResearcherAgent;
import com.agent.editor.agent.tool.ExecutionToolAccessRole;
import com.agent.editor.service.StructuredDocumentService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupervisorAgentConfig {

    @Bean
    public SupervisorAgent supervisorAgentDefinition(ChatModel chatModel,
                                                     SupervisorContextFactory supervisorContextFactory) {
        return new HybridSupervisorAgent(chatModel, supervisorContextFactory);
    }

    @Bean
    public ResearcherAgentContextFactory researcherAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                       MemoryCompressor memoryCompressor) {
        return new ResearcherAgentContextFactory(memoryChatMessageMapper, memoryCompressor);
    }

    @Bean
    public GroundedWriterAgentContextFactory groundedWriterAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                               MemoryCompressor memoryCompressor,
                                                                               StructuredDocumentService structuredDocumentService) {
        return new GroundedWriterAgentContextFactory(memoryChatMessageMapper, memoryCompressor, structuredDocumentService);
    }

    @Bean
    public EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                                                   MemoryCompressor memoryCompressor,
                                                                                   StructuredDocumentService structuredDocumentService) {
        return new EvidenceReviewerAgentContextFactory(memoryChatMessageMapper, memoryCompressor, structuredDocumentService);
    }

    @Bean
    public MemoryAgentContextFactory memoryAgentContextFactory(ExecutionMemoryChatMessageMapper memoryChatMessageMapper,
                                                               MemoryCompressor memoryCompressor) {
        return new MemoryAgentContextFactory(memoryChatMessageMapper, memoryCompressor);
    }

    @Bean
    public ResearcherAgent researcherAgentDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                                     ResearcherAgentContextFactory researcherAgentContextFactory) {
        return ResearcherAgent.streaming(streamingLLMInvoker, researcherAgentContextFactory);
    }

    @Bean
    public GroundedWriterAgent groundedWriterAgentDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                                             GroundedWriterAgentContextFactory groundedWriterAgentContextFactory) {
        return GroundedWriterAgent.streaming(streamingLLMInvoker, groundedWriterAgentContextFactory);
    }

    @Bean
    public EvidenceReviewerAgent evidenceReviewerAgentDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                                                 EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory) {
        return EvidenceReviewerAgent.streaming(streamingLLMInvoker, evidenceReviewerAgentContextFactory);
    }

    @Bean
    public MemoryAgent memoryAgentDefinition(StreamingLLMInvoker streamingLLMInvoker,
                                             MemoryAgentContextFactory memoryAgentContextFactory) {
        return MemoryAgent.streaming(streamingLLMInvoker, memoryAgentContextFactory);
    }

    @Bean
    public WorkerRegistry workerRegistry(ResearcherAgent researcherAgentDefinition,
                                         GroundedWriterAgent groundedWriterAgentDefinition,
                                         EvidenceReviewerAgent evidenceReviewerAgentDefinition,
                                         MemoryAgent memoryAgentDefinition) {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.RESEARCHER,
                "Researcher",
                "Collect grounded evidence from the knowledge base before downstream writing or review.",
                researcherAgentDefinition,
                ExecutionToolAccessRole.RESEARCH
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Produce grounded document updates and revisions without introducing unsupported claims.",
                groundedWriterAgentDefinition,
                ExecutionToolAccessRole.MAIN_WRITE
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.REVIEWER,
                "Reviewer",
                "Review whether the response follows the user instruction and remains grounded in available evidence.",
                evidenceReviewerAgentDefinition,
                ExecutionToolAccessRole.REVIEW
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.MEMORY,
                "Memory",
                "Retrieve and maintain durable document constraints for the current document.",
                memoryAgentDefinition,
                ExecutionToolAccessRole.MEMORY
        ));
        return workerRegistry;
    }
}

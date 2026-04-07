package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.core.memory.MemoryCompressor;
import com.agent.editor.agent.v2.model.StreamingLLMInvoker;
import com.agent.editor.agent.v2.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.v2.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.v2.supervisor.routing.HybridSupervisorAgent;
import com.agent.editor.agent.v2.supervisor.worker.*;
import com.agent.editor.agent.v2.supervisor.worker.ResearcherAgent;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
import com.agent.editor.agent.v2.tool.memory.MemoryToolNames;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SupervisorAgentConfig {

    @Bean
    public SupervisorAgent supervisorAgentDefinition(ChatModel chatModel,
                                                     SupervisorContextFactory supervisorContextFactory) {
        return new HybridSupervisorAgent(chatModel, supervisorContextFactory);
    }

    @Bean
    public ResearcherAgentContextFactory researcherAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new ResearcherAgentContextFactory(memoryCompressor);
    }

    @Bean
    public GroundedWriterAgentContextFactory groundedWriterAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new GroundedWriterAgentContextFactory(memoryCompressor);
    }

    @Bean
    public EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new EvidenceReviewerAgentContextFactory(memoryCompressor);
    }

    @Bean
    public MemoryAgentContextFactory memoryAgentContextFactory(MemoryCompressor memoryCompressor) {
        return new MemoryAgentContextFactory(memoryCompressor);
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
                List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE),
                List.of("research"),
                ExecutionToolAccessRole.RESEARCH
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Produce grounded document updates and revisions without introducing unsupported claims.",
                groundedWriterAgentDefinition,
                List.of(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.PATCH_DOCUMENT_NODE,
                        DocumentToolNames.EDIT_DOCUMENT,
                        DocumentToolNames.APPEND_TO_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                        DocumentToolNames.SEARCH_CONTENT
                ),
                List.of("write", "edit"),
                ExecutionToolAccessRole.MAIN_WRITE
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.REVIEWER,
                "Reviewer",
                "Review whether the response follows the user instruction and remains grounded in available evidence.",
                evidenceReviewerAgentDefinition,
                List.of(
                        DocumentToolNames.READ_DOCUMENT_NODE,
                        DocumentToolNames.SEARCH_CONTENT,
                        DocumentToolNames.ANALYZE_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT
                ),
                List.of("review"),
                ExecutionToolAccessRole.REVIEW
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.MEMORY,
                "Memory",
                "Retrieve and maintain durable document constraints for the current document.",
                memoryAgentDefinition,
                List.of(
                        MemoryToolNames.SEARCH_MEMORY,
                        MemoryToolNames.UPSERT_MEMORY
                ),
                List.of("memory"),
                ExecutionToolAccessRole.MEMORY
        ));
        return workerRegistry;
    }
}

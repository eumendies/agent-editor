package com.agent.editor.config;

import com.agent.editor.agent.v2.core.agent.SupervisorAgent;
import com.agent.editor.agent.v2.core.context.SupervisorContext;
import com.agent.editor.agent.v2.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.v2.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.v2.supervisor.routing.HybridSupervisorAgent;
import com.agent.editor.agent.v2.supervisor.worker.*;
import com.agent.editor.agent.v2.supervisor.worker.ResearcherAgent;
import com.agent.editor.agent.v2.tool.document.DocumentToolNames;
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
    public ResearcherAgentContextFactory researcherAgentContextFactory() {
        return new ResearcherAgentContextFactory();
    }

    @Bean
    public GroundedWriterAgentContextFactory groundedWriterAgentContextFactory() {
        return new GroundedWriterAgentContextFactory();
    }

    @Bean
    public EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory() {
        return new EvidenceReviewerAgentContextFactory();
    }

    @Bean
    public ResearcherAgent researcherAgentDefinition(ChatModel chatModel,
                                                     ResearcherAgentContextFactory researcherAgentContextFactory) {
        return new ResearcherAgent(chatModel, researcherAgentContextFactory);
    }

    @Bean
    public GroundedWriterAgent groundedWriterAgentDefinition(ChatModel chatModel,
                                                             GroundedWriterAgentContextFactory groundedWriterAgentContextFactory) {
        return new GroundedWriterAgent(chatModel, groundedWriterAgentContextFactory);
    }

    @Bean
    public EvidenceReviewerAgent evidenceReviewerAgentDefinition(ChatModel chatModel,
                                                                 EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory) {
        return new EvidenceReviewerAgent(chatModel, evidenceReviewerAgentContextFactory);
    }

    @Bean
    public WorkerRegistry workerRegistry(ResearcherAgent researcherAgentDefinition,
                                         GroundedWriterAgent groundedWriterAgentDefinition,
                                         EvidenceReviewerAgent evidenceReviewerAgentDefinition) {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.RESEARCHER,
                "Researcher",
                "Collect grounded evidence from the knowledge base before downstream writing or review.",
                researcherAgentDefinition,
                List.of(DocumentToolNames.RETRIEVE_KNOWLEDGE),
                List.of("research")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.WRITER,
                "Writer",
                "Produce grounded document updates and revisions without introducing unsupported claims.",
                groundedWriterAgentDefinition,
                List.of(
                        DocumentToolNames.EDIT_DOCUMENT,
                        DocumentToolNames.APPEND_TO_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT,
                        DocumentToolNames.SEARCH_CONTENT
                ),
                List.of("write", "edit")
        ));
        workerRegistry.register(new SupervisorContext.WorkerDefinition(
                SupervisorWorkerIds.REVIEWER,
                "Reviewer",
                "Review whether the response follows the user instruction and remains grounded in available evidence.",
                evidenceReviewerAgentDefinition,
                List.of(
                        DocumentToolNames.SEARCH_CONTENT,
                        DocumentToolNames.ANALYZE_DOCUMENT,
                        DocumentToolNames.GET_DOCUMENT_SNAPSHOT
                ),
                List.of("review")
        ));
        return workerRegistry;
    }
}

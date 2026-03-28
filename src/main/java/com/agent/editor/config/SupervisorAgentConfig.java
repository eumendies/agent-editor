package com.agent.editor.config;

import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.routing.HybridSupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.worker.*;
import com.agent.editor.agent.v2.supervisor.worker.ResearcherAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SupervisorAgentConfig {

    @Bean
    public SupervisorAgentDefinition supervisorAgentDefinition(ChatModel chatModel) {
        return new HybridSupervisorAgentDefinition(chatModel);
    }

    @Bean
    public ResearcherAgent researcherAgentDefinition(ChatModel chatModel) {
        return new ResearcherAgent(chatModel);
    }

    @Bean
    public GroundedWriterAgent groundedWriterAgentDefinition(ChatModel chatModel) {
        return new GroundedWriterAgent(chatModel);
    }

    @Bean
    public EvidenceReviewerAgent evidenceReviewerAgentDefinition(ChatModel chatModel) {
        return new EvidenceReviewerAgent(chatModel);
    }

    @Bean
    public WorkerRegistry workerRegistry(ResearcherAgent researcherAgentDefinition,
                                         GroundedWriterAgent groundedWriterAgentDefinition,
                                         EvidenceReviewerAgent evidenceReviewerAgentDefinition) {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        workerRegistry.register(new WorkerDefinition(
                "researcher",
                "Researcher",
                "Collect grounded evidence from the knowledge base before downstream writing or review.",
                researcherAgentDefinition,
                List.of("retrieveKnowledge"),
                List.of("research")
        ));
        workerRegistry.register(new WorkerDefinition(
                "writer",
                "Writer",
                "Produce grounded document updates and revisions without introducing unsupported claims.",
                groundedWriterAgentDefinition,
                List.of("editDocument", "searchContent"),
                List.of("write", "edit")
        ));
        workerRegistry.register(new WorkerDefinition(
                "reviewer",
                "Reviewer",
                "Review whether the response follows the user instruction and remains grounded in available evidence.",
                evidenceReviewerAgentDefinition,
                List.of("searchContent", "analyzeDocument"),
                List.of("review")
        ));
        return workerRegistry;
    }
}

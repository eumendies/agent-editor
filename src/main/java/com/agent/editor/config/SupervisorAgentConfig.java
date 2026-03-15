package com.agent.editor.config;

import com.agent.editor.agent.v2.react.ReactAgentDefinition;
import com.agent.editor.agent.v2.supervisor.HybridSupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.SupervisorAgentDefinition;
import com.agent.editor.agent.v2.supervisor.WorkerDefinition;
import com.agent.editor.agent.v2.supervisor.WorkerRegistry;
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
    public WorkerRegistry workerRegistry(ReactAgentDefinition reactAgentDefinition) {
        WorkerRegistry workerRegistry = new WorkerRegistry();
        // 第一版先用异构 worker 池把角色边界和工具边界立住，后面再升级为动态注册或能力发现。
        workerRegistry.register(new WorkerDefinition(
                "analyzer",
                "Analyzer",
                "Inspect the document and identify issues before changes are made.",
                reactAgentDefinition,
                List.of("searchContent", "analyzeDocument"),
                List.of("analyze")
        ));
        workerRegistry.register(new WorkerDefinition(
                "editor",
                "Editor",
                "Draft, rewrite, and apply concrete edits to the document, including writing from scratch when needed.",
                reactAgentDefinition,
                List.of("editDocument", "searchContent"),
                List.of("edit", "draft")
        ));
        workerRegistry.register(new WorkerDefinition(
                "reviewer",
                "Reviewer",
                "Review the revised document and flag any remaining issues.",
                reactAgentDefinition,
                List.of("searchContent", "analyzeDocument"),
                List.of("review")
        ));
        return workerRegistry;
    }
}

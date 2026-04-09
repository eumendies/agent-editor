package com.agent.editor.agent.core.context;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.memory.MemoryCompressionResult;
import com.agent.editor.agent.core.memory.MemoryCompressor;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.core.state.ExecutionStage;
import com.agent.editor.agent.planning.PlanningAgentContextFactory;
import com.agent.editor.agent.react.ReactAgentContextFactory;
import com.agent.editor.agent.reflexion.ReflexionActorContextFactory;
import com.agent.editor.agent.reflexion.ReflexionCritique;
import com.agent.editor.agent.reflexion.ReflexionCriticContextFactory;
import com.agent.editor.agent.reflexion.ReflexionVerdict;
import com.agent.editor.agent.supervisor.SupervisorContextFactory;
import com.agent.editor.agent.supervisor.SupervisorWorkerIds;
import com.agent.editor.agent.supervisor.worker.EvidenceReviewerAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.GroundedWriterAgentContextFactory;
import com.agent.editor.agent.supervisor.worker.ResearcherAgentContextFactory;
import com.agent.editor.agent.task.TaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextMemoryCompressionAspectTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldCompressAnnotatedFactoryMethodsThroughSpringProxy() {
        contextRunner.run(context -> {
            ReactAgentContextFactory reactAgentContextFactory = context.getBean("reactAgentContextFactory", ReactAgentContextFactory.class);

            assertThat(AopUtils.isAopProxy(reactAgentContextFactory)).isTrue();

            AgentRunContext preparedContext = reactAgentContextFactory.prepareInitialContext(new TaskRequest(
                    "task-1",
                    "session-1",
                    AgentType.REACT,
                    new DocumentSnapshot("doc-1", "Title", "body"),
                    "rewrite this",
                    3,
                    new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("previous turn")))
            ));

            ChatTranscriptMemory memory = (ChatTranscriptMemory) preparedContext.getMemory();
            assertThat(memory.getMessages())
                    .singleElement()
                    .satisfies(message -> assertThat(message.getText()).isEqualTo("compressed by aspect"));
        });
    }

    @Test
    void shouldCompressAllAnnotatedInitialContextMethodsThroughSpringProxy() {
        contextRunner.run(context -> {
            assertCompressed(context.getBean("reactAgentContextFactory", ReactAgentContextFactory.class).prepareInitialContext(taskRequest(AgentType.REACT, "rewrite this")));
            assertCompressed(context.getBean("planningAgentContextFactory", PlanningAgentContextFactory.class).prepareInitialContext(taskRequest(AgentType.PLANNING, "plan this")));
            assertCompressed(context.getBean("reflexionActorContextFactory", ReflexionActorContextFactory.class).prepareInitialContext(taskRequest(AgentType.REFLEXION, "revise this")));
            assertCompressed(context.getBean("reflexionCriticContextFactory", ReflexionCriticContextFactory.class).prepareInitialContext(taskRequest(AgentType.REFLEXION, "review this")));
            assertCompressed(context.getBean("supervisorContextFactory", SupervisorContextFactory.class).prepareInitialContext(taskRequest(AgentType.SUPERVISOR, "route this")));
            assertCompressed(context.getBean("researcherAgentContextFactory", ResearcherAgentContextFactory.class).prepareInitialContext(taskRequest(AgentType.REACT, "gather evidence")));
            assertCompressed(context.getBean("groundedWriterAgentContextFactory", GroundedWriterAgentContextFactory.class).prepareInitialContext(taskRequest(AgentType.REACT, "write grounded draft")));
            assertCompressed(context.getBean("evidenceReviewerAgentContextFactory", EvidenceReviewerAgentContextFactory.class).prepareInitialContext(taskRequest(AgentType.REACT, "review evidence")));
        });
    }

    @Test
    void shouldCompressPlanningAnnotatedMethodsThroughSpringProxy() {
        contextRunner.run(context -> {
            PlanningAgentContextFactory planningAgentContextFactory = context.getBean(PlanningAgentContextFactory.class);

            assertThat(AopUtils.isAopProxy(planningAgentContextFactory)).isTrue();
            assertCompressed(planningAgentContextFactory.prepareExecutionInitialContext(taskRequest(AgentType.PLANNING, "plan this")));
            assertCompressed(planningAgentContextFactory.prepareExecutionStepContext(
                    planningState(),
                    new com.agent.editor.agent.core.agent.PlanResult().new PlanStep(1, "Refine outline")
            ));
            assertCompressed(planningAgentContextFactory.summarizeCompletedStep(
                    planningState(),
                    new ExecutionResult<>("done", "step summary", "draft updated")
            ));
        });
    }

    @Test
    void shouldCompressReflexionAnnotatedMethodsThroughSpringProxy() {
        contextRunner.run(context -> {
            ReflexionActorContextFactory actorContextFactory = context.getBean(ReflexionActorContextFactory.class);
            ReflexionCriticContextFactory criticContextFactory = context.getBean(ReflexionCriticContextFactory.class);

            assertThat(AopUtils.isAopProxy(actorContextFactory)).isTrue();
            assertThat(AopUtils.isAopProxy(criticContextFactory)).isTrue();
            assertCompressed(actorContextFactory.prepareRevisionContext(
                    taskRequest(AgentType.REFLEXION, "revise this"),
                    reflexionState(),
                    2,
                    new ReflexionCritique(ReflexionVerdict.REVISE, "tighten intro", "too long")
            ));
            assertCompressed(criticContextFactory.prepareReviewContext(
                    taskRequest(AgentType.REFLEXION, "review this"),
                    reflexionState(),
                    "actor summary"
            ));
        });
    }

    @Test
    void shouldCompressSupervisorSummaryMethodThroughSpringProxy() {
        contextRunner.run(context -> {
            SupervisorContextFactory supervisorContextFactory = context.getBean(SupervisorContextFactory.class);

            assertThat(AopUtils.isAopProxy(supervisorContextFactory)).isTrue();
            assertCompressed(supervisorContextFactory.summarizeWorkerResult(
                    supervisorState(),
                    SupervisorWorkerIds.WRITER,
                    new ExecutionResult<>("done", "worker summary", "draft updated")
            ));
        });
    }

    @Test
    void shouldKeepSupervisorWorkerContextCompressionOrderAsManualLogic() {
        contextRunner.run(context -> {
            SupervisorContextFactory supervisorContextFactory = context.getBean(SupervisorContextFactory.class);

            AgentRunContext conversationState = new AgentRunContext(
                    null,
                    1,
                    "draft",
                    new ChatTranscriptMemory(List.of(
                            new ChatMessage.UserChatMessage("previous worker result"),
                            new ChatMessage.ToolExecutionResultChatMessage("tool-1", "retrieveKnowledge", "{}", "raw tool transcript")
                    )),
                    ExecutionStage.RUNNING,
                    null,
                    List.of()
            );

            AgentRunContext workerContext = supervisorContextFactory.buildWorkerExecutionContext(
                    conversationState,
                    "draft",
                    "gather more evidence"
            );

            ChatTranscriptMemory memory = (ChatTranscriptMemory) workerContext.getMemory();
            assertThat(memory.getMessages())
                    .extracting(ChatMessage::getText)
                    .containsExactly("compressed by aspect", "gather more evidence");
        });
    }

    private static TaskRequest taskRequest(AgentType agentType, String instruction) {
        return new TaskRequest(
                "task-1",
                "session-1",
                agentType,
                new DocumentSnapshot("doc-1", "Title", "body"),
                instruction,
                3,
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("previous turn")))
        );
    }

    private static AgentRunContext planningState() {
        return new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("previous turn"))),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    private static AgentRunContext reflexionState() {
        return new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("previous turn"),
                        new ChatMessage.AiChatMessage("draft summary")
                )),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    private static AgentRunContext supervisorState() {
        return new AgentRunContext(
                null,
                1,
                "draft",
                new ChatTranscriptMemory(List.of(new ChatMessage.UserChatMessage("previous worker result"))),
                ExecutionStage.RUNNING,
                null,
                List.of()
        );
    }

    private static void assertCompressed(AgentRunContext context) {
        ChatTranscriptMemory memory = (ChatTranscriptMemory) context.getMemory();
        assertThat(memory.getMessages())
                .singleElement()
                .satisfies(message -> assertThat(message.getText()).isEqualTo("compressed by aspect"));
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        ContextMemoryCompressionAspect contextMemoryCompressionAspect() {
            return new ContextMemoryCompressionAspect();
        }

        @Bean
        MemoryCompressor memoryCompressor() {
            return request -> new MemoryCompressionResult(
                    new ChatTranscriptMemory(List.of(new ChatMessage.AiChatMessage("compressed by aspect"))),
                    true,
                    "compressed"
            );
        }

        @Bean
        ReactAgentContextFactory reactAgentContextFactory(MemoryCompressor memoryCompressor) {
            return new ReactAgentContextFactory(memoryCompressor);
        }

        @Bean
        SupervisorContextFactory supervisorContextFactory(MemoryCompressor memoryCompressor) {
            return new SupervisorContextFactory(memoryCompressor);
        }

        @Bean
        PlanningAgentContextFactory planningAgentContextFactory(MemoryCompressor memoryCompressor) {
            return new PlanningAgentContextFactory(memoryCompressor);
        }

        @Bean
        ReflexionActorContextFactory reflexionActorContextFactory(MemoryCompressor memoryCompressor) {
            return new ReflexionActorContextFactory(memoryCompressor);
        }

        @Bean
        ReflexionCriticContextFactory reflexionCriticContextFactory(MemoryCompressor memoryCompressor) {
            return new ReflexionCriticContextFactory(memoryCompressor);
        }

        @Bean
        ResearcherAgentContextFactory researcherAgentContextFactory(MemoryCompressor memoryCompressor) {
            return new ResearcherAgentContextFactory(memoryCompressor);
        }

        @Bean
        GroundedWriterAgentContextFactory groundedWriterAgentContextFactory(MemoryCompressor memoryCompressor) {
            return new GroundedWriterAgentContextFactory(memoryCompressor);
        }

        @Bean
        EvidenceReviewerAgentContextFactory evidenceReviewerAgentContextFactory(MemoryCompressor memoryCompressor) {
            return new EvidenceReviewerAgentContextFactory(memoryCompressor);
        }
    }
}

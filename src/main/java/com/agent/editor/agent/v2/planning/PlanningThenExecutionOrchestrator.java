package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;

/**
 * 两阶段编排：先由 planner 拆任务，再把每个 plan step 交给执行 agent 串行落地。
 */
public class PlanningThenExecutionOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime planningRuntime;
    private final PlanningAgentImpl planningAgentImpl;
    private final ExecutionRuntime toolLoopExecutionRuntime;
    private final Agent executionAgent;
    private final PlanningAgentContextFactory planningContextFactory;

    public PlanningThenExecutionOrchestrator(ExecutionRuntime planningRuntime,
                                             PlanningAgentImpl planningAgentImpl,
                                             ExecutionRuntime toolLoopExecutionRuntime,
                                             Agent executionAgent,
                                             PlanningAgentContextFactory planningContextFactory) {
        this.planningRuntime = planningRuntime;
        this.planningAgentImpl = planningAgentImpl;
        this.toolLoopExecutionRuntime = toolLoopExecutionRuntime;
        this.executionAgent = executionAgent;
        this.planningContextFactory = planningContextFactory;
    }

    /**
     * 先生成计划，再按计划步骤串行执行编辑。
     *
     * @param request 任务输入
     * @return 所有计划步骤执行完成后的任务结果
     */
    @Override
    public TaskResult execute(TaskRequest request) {
        // planning 与 execution 都通过 runtime 承接，编排层只负责阶段串联。
        AgentRunContext planningState = planningContextFactory.prepareInitialContext(request);
        ExecutionResult<PlanResult> planningResult = planningRuntime.run(
                planningAgentImpl,
                new ExecutionRequest(
                        request.getTaskId(),
                        request.getSessionId(),
                        AgentType.PLANNING,
                        request.getDocument(),
                        request.getInstruction(),
                        request.getMaxIterations()
                ),
                planningState
        );
        PlanResult plan = planningResult.getResult();

        AgentRunContext executionPlanningState = planningContextFactory.prepareExecutionInitialContext(request);
        String currentContent = request.getDocument().getContent();
        for (PlanResult.PlanStep step : plan.getPlans()) {
            AgentRunContext stepState = planningContextFactory.prepareExecutionStepContext(executionPlanningState, step);
            // 每个步骤都基于上一步的文档内容继续执行，形成显式的阶段性产物传递。
            ExecutionResult result = toolLoopExecutionRuntime.run(
                    executionAgent,
                    new ExecutionRequest(
                            request.getTaskId(),
                            request.getSessionId(),
                            AgentType.REACT,
                            new DocumentSnapshot(
                                    request.getDocument().getDocumentId(),
                                    request.getDocument().getTitle(),
                                    currentContent
                            ),
                            step.getInstruction(),
                            request.getMaxIterations()
                    ),
                    stepState
            );
            // 每一步都把上一步的产物作为新的文档输入，形成显式串行阶段链。
            currentContent = result.getFinalContent();
            executionPlanningState = planningContextFactory.summarizeCompletedStep(stepState, result);
        }

        return new TaskResult(TaskStatus.COMPLETED, currentContent, executionPlanningState.getMemory());
    }
}

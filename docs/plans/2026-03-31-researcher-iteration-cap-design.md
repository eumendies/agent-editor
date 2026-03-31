# Researcher Iteration Cap Design

**Context**

`SupervisorOrchestrator` currently passes `TaskRequest.maxIterations` straight through to every worker `ExecutionRequest`. That keeps worker loop budgets uniform, but it is too loose for `ResearcherAgent`, whose retrieval loop now starts with a fixed first-pass retrieval and should converge quickly. Letting researcher inherit a double-digit budget increases the chance of excessive rewrite/retry loops without improving result quality.

**Options**

1. Special-case researcher inside `SupervisorOrchestrator`
   - Detect the researcher worker id when building the worker `ExecutionRequest`
   - Clamp its `maxIterations` to `4`
   - Leave every other worker unchanged

2. Add per-worker iteration caps to `WorkerDefinition`
   - More extensible
   - Heavier change surface for a single current need

3. Let each worker self-cap inside its own runtime
   - Spreads scheduling policy across workers
   - Makes orchestration harder to reason about

**Decision**

Use option 1. Keep the policy in `SupervisorOrchestrator`, where worker scheduling decisions already live. Clamp researcher to `min(taskMaxIterations, 4)` so low task budgets still apply, while large task budgets no longer give researcher an unnecessary loop allowance.

**Design**

- Add a small helper in `SupervisorOrchestrator` to resolve the worker-specific iteration cap.
- If the selected worker id is `researcher`, return `Math.min(request.getMaxIterations(), 4)`.
- Otherwise, return `request.getMaxIterations()`.
- Use that resolved value when creating the worker `ExecutionRequest`.

This change intentionally does not alter:

- supervisor outer dispatch budget
- non-researcher worker iteration budgets
- `ResearcherAgent` internal decision logic

**Testing**

- Add a failing test in `SupervisorOrchestratorTest` asserting that a researcher assignment receives `maxIterations == 4` even when the task budget is larger.
- Add a companion assertion that non-researcher workers still receive the original task budget.
- Run focused orchestrator tests after the change.

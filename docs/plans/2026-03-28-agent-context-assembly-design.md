# Agent Context Factory Design

**Goal:** Introduce a top-level `AgentContextFactory` abstraction so context assembly and model message construction are removed from runtimes, orchestrators, and agent classes.

## Problem

The current v2 agent stack mixes four responsibilities across the wrong layers:

- runtimes sometimes append transcript messages
- orchestrators sometimes hand-build invocation memory
- agents build prompt messages directly from `AgentRunContext`
- request fields like `instruction` are used both as control input and transcript content

This leads to duplicated and ambiguous behavior, especially for:

- sub-agent invocation
- resumed execution
- planning step execution
- reflexion critic review
- multi-round tool loops

The same instruction can be injected multiple times, and there is no single place to audit what context the model actually sees.

## Chosen Architecture

Introduce a top-level interface named `AgentContextFactory`.

This factory is responsible for two layers of transformation:

1. **Invocation context assembly**
   Build the correct `AgentRunContext` for entering or continuing an agent run.

2. **Model invocation assembly**
   Build the exact model-facing request inputs from that `AgentRunContext`.

This separates:

- runtime state
- orchestration semantics
- model prompt/message construction
- model-call options such as tools and JSON response format

## Core Types

### `AgentContextFactory`

Top-level contract. It should own at least these responsibilities:

- `prepareInitialContext(...)`
- `prepareFollowupContext(...)`
- `buildModelInvocationContext(AgentRunContext context)`

Exact method names can be refined, but the contract must cover:

- context entry construction
- follow-up/sub-step construction
- model-facing request assembly

### `ModelInvocationContext`

This is a value object describing one concrete model call. It should contain:

- `messages`
- `toolSpecifications`
- `responseFormat`

Optional future fields can be added later, such as:

- temperature override
- max token override
- provider-specific flags

### `AgentRunContext`

This remains the runtime-facing execution state object. It should no longer be treated as the same thing as the model request payload.

`AgentRunContext` is for:

- current content
- memory
- iteration/stage
- request metadata

`ModelInvocationContext` is for:

- exactly what is sent to the model on this turn

## Layer Responsibilities

### Orchestrator

Should only:

- choose which agent/factory to invoke
- decide when to transition between phases
- request the next prepared context from the factory

Should not:

- append ad-hoc transcript messages itself
- hand-build model messages

### Runtime

Should only:

- bind request/runtime metadata
- run tool loops
- append tool call/result messages that are intrinsic runtime output

Should not:

- inject `request.instruction` into memory automatically
- construct model messages

### Agent

Should only:

- ask its factory for `ModelInvocationContext`
- call the model
- translate `AiMessage` into `ToolLoopDecision`

Should not:

- own `buildMessages`, `buildSystemPrompt`, `buildUserPrompt`
- infer whether instruction has already been ingested into transcript memory

### AgentContextFactory

Should own:

- user-entry context assembly
- sub-step/follow-up context assembly
- transcript injection policy
- system/user/ai message composition
- model-call options such as tools and response format

## Factory Implementations

Factories should be split by **agent paradigm**, not by individual concrete class unless a paradigm diverges enough to justify it.

Initial recommended implementations:

- `ReactAgentContextFactory`
- `PlanningAgentContextFactory`
- `ReflexionActorContextFactory`
- `ReflexionCriticContextFactory`

Supervisor workers can be handled in a follow-up step:

- either a shared worker factory base class
- or worker-specific factories if their prompt contracts diverge too much

## Why This Is Better

- One place defines what memory is injected and when
- One place defines what the model actually sees
- Runtimes become deterministic and simpler
- Orchestrators stop mixing control flow with transcript engineering
- Agent classes become thin model adapters rather than prompt builders
- Sub-agent calls stop depending on hidden runtime behavior

## Migration Strategy

Do not attempt a full repo-wide rewrite at once.

Recommended order:

1. Define `AgentContextFactory` and `ModelInvocationContext`
2. Migrate `ReflexionCritic`
   Reason: it currently has the most complex message/response-format/tool logic
3. Migrate `ReactAgent`
4. Migrate orchestrator-side context preparation for React/Planning/Reflexion
5. Migrate supervisor workers

## First-Step Constraints

For the first refactor pass:

- do not change tool loop behavior
- do not change event contracts
- do not redesign `AgentRunContext` itself yet
- do not attempt to generalize every worker pattern prematurely

The main goal is to establish the correct ownership boundary:

`AgentRunContext` is runtime state, and `AgentContextFactory` owns the transformation from runtime state to model request.

# Context Factory Memory Compression Design

**Date:** 2026-04-02

## Goal

Use Spring AOP to apply memory compression to selected `ContextFactory` methods that create or evolve `AgentRunContext`, so the factories no longer need repeated explicit `compressContextMemory(...)` calls in the common path.

## Scope

This design only covers the current explicit whitelist of public context-construction methods in agent v2 context factories.

In scope:
- `prepareInitialContext(...)`
- `prepareExecutionInitialContext(...)`
- `prepareExecutionStepContext(...)`
- `summarizeCompletedStep(...)`
- `prepareRevisionContext(...)`
- `prepareReviewContext(...)`
- `summarizeWorkerResult(...)`

Out of scope for this pass:
- `buildWorkerExecutionContext(...)` in `SupervisorContextFactory`

## Why Not Blanket Interception

The repository has multiple `ContextFactory` implementations, but not all `AgentRunContext`-returning methods are semantically identical.

`SupervisorContextFactory.buildWorkerExecutionContext(...)` currently does this in a specific order:
1. Compress existing conversation memory
2. Drop tool-call/result details from the compressed memory
3. Append the current worker instruction as the last user turn

If this method were switched to a generic "compress the returned context" advice, the appended worker instruction would also be part of the compression input, which changes behavior. That ordering is covered by tests and should remain unchanged.

## Recommended Approach

### 1. Add an explicit annotation

Create a runtime annotation such as `@CompressContextMemory` and place it only on the public factory methods that should be compressed on return.

This keeps the boundary visible in code review and avoids hiding the allowlist inside a pointcut string.

### 2. Add a capability interface for target factories

Introduce a small interface that exposes the `MemoryCompressor` used by the factory, for example:
- `MemoryCompressionCapableContextFactory`

The aspect will require this interface so it can compress the returned `AgentRunContext` without using reflection.

### 3. Add a Spring aspect

Implement an `@Around` advice that:
- matches methods annotated with `@CompressContextMemory`
- requires the target bean to implement `MemoryCompressionCapableContextFactory`
- executes the original method
- if the return value is an `AgentRunContext`, replaces its memory with `memoryCompressor.compressOrOriginal(...)`

## Data Flow

For annotated methods, the new flow is:

1. Factory method builds the uncompressed `AgentRunContext`
2. Spring AOP intercepts the call on the bean proxy
3. Aspect compresses `context.getMemory()`
4. Caller receives the compressed `AgentRunContext`

For `buildWorkerExecutionContext(...)`, the existing local compression stays inside the method.

## Code Changes

### New types

- `com.agent.editor.agent.v2.core.context.CompressContextMemory`
- `com.agent.editor.agent.v2.core.context.MemoryCompressionCapableContextFactory`
- `com.agent.editor.agent.v2.core.context.ContextMemoryCompressionAspect`

### Existing classes to update

- `ReactAgentContextFactory`
- `PlanningAgentContextFactory`
- `ReflexionCriticContextFactory`
- `ReflexionActorContextFactory`
- `SupervisorContextFactory`
- `ResearcherAgentContextFactory`
- `GroundedWriterAgentContextFactory`
- `EvidenceReviewerAgentContextFactory`

### Build configuration

Add `spring-boot-starter-aop` to `pom.xml`.

## Testing Strategy

### Unit/integration coverage

1. Add a Spring-backed test for the aspect itself:
   - call annotated factory methods through beans
   - verify returned memory is compressed
   - verify an unannotated method is not compressed by the aspect

2. Update factory tests that currently instantiate factories directly:
   - keep direct-construction tests for methods whose behavior does not depend on AOP internals
   - add dedicated Spring context tests where proxy behavior must be validated

3. Preserve the supervisor special case:
   - keep or extend the existing test proving `buildWorkerExecutionContext(...)` compresses before filtering tool details

## Risks

- Direct `new ContextFactory(...)` usage does not go through Spring AOP. In this repository that mainly affects tests, so tests need to validate proxy-based behavior explicitly.
- If future developers add a new context-creation method and forget the annotation, compression will be skipped. This is an accepted tradeoff because the user requested the explicit whitelist model.

## Acceptance Criteria

- Common context-creation methods no longer call `compressContextMemory(...)` directly
- Compression still happens when those methods are invoked through Spring beans
- `SupervisorContextFactory.buildWorkerExecutionContext(...)` keeps its current ordering semantics
- Relevant tests pass

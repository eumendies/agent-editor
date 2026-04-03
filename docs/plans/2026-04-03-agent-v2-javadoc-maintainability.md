# Agent V2 Javadoc Maintainability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve maintainability in `agent/v2` by adding Javadoc to the most important public methods and workflow entry points.

**Architecture:** Focus on method-level documentation for orchestration, runtime, context-assembly, and registry APIs that define the execution flow. Skip trivial DTO accessors and constructor boilerplate so the added docs stay high-signal.

**Tech Stack:** Java 17, Spring Boot, Lombok, LangChain4j, Maven

---

### Task 1: Identify high-signal documentation targets

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/agent/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/context/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/*.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolRegistry.java`

**Step 1: Read the package entry points**

Review the public APIs that control agent execution, workflow dispatch, memory assembly, and tool exposure.

**Step 2: Exclude low-value targets**

Skip DTO getters/setters, simple constructors, and trivial value objects where Javadoc would duplicate field names.

### Task 2: Add Javadoc to workflow contracts and runtimes

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/agent/PlanningAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/agent/SupervisorAgent.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/task/TaskOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/PlanningExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/SupervisorExecutionRuntime.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/runtime/ToolLoopExecutionRuntime.java`

**Step 1: Document input/output expectations**

Explain what each runtime executes, how `initialContext` is used, and what result shape callers can rely on.

**Step 2: Document non-obvious runtime semantics**

Call out state ownership, completion behavior, and loop semantics for methods such as `runInternal(...)`.

### Task 3: Add Javadoc to orchestration and context assembly APIs

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/core/context/AgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/core/context/AgentRunContext.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionCriticContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningAgentContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/supervisor/SupervisorContextFactory.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/tool/ToolRegistry.java`

**Step 1: Document stage transition helpers**

Explain how immutable-style methods on `AgentRunContext` evolve content, iteration, and memory.

**Step 2: Document orchestration boundaries**

Clarify what each orchestrator coordinates, what context factories inject into prompts, and how worker/critic execution is bridged.

### Task 4: Verify no syntax regressions

**Files:**
- Verify only

**Step 1: Run compile verification**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -q -DskipTests compile`

Expected: build succeeds without Java syntax or Javadoc-related compilation regressions.

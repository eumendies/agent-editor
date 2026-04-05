# Execution Tool Access Policy Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Centralize final execution tool assembly in `ExecutionToolAccessPolicy` so orchestrators no longer manually compose document tools with memory tools.

**Architecture:** Keep document and memory access decisions in separate domain policies, then add an execution-level composition policy that maps execution roles to domain policies and returns the final immutable tool list. Update only the main execution orchestrators to use this new policy, leaving supervisor worker routing on the existing document policy.

**Tech Stack:** Java 17, Spring Boot, JUnit 5

---

### Task 1: Lock the new policy behavior with tests

**Files:**
- Create: `src/test/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicyTest.java`
- Create: `src/test/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicyTest.java`

**Step 1: Write the failing tests**

Add tests asserting:

- `MemoryToolAccessPolicy.allowedTools(MAIN_WRITE)` returns both memory tools
- `MemoryToolAccessPolicy.allowedTools(REVIEW/RESEARCH)` returns empty
- `ExecutionToolAccessPolicy.allowedTools(..., MAIN_WRITE)` returns document write tools followed by memory tools
- `ExecutionToolAccessPolicy.allowedTools(..., REVIEW/RESEARCH)` do not include memory tools

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest test`

Expected: FAIL because the new classes do not exist yet

**Step 3: Write minimal implementation**

Create:

- `src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessPolicy.java`
- `src/main/java/com/agent/editor/agent/v2/tool/ExecutionToolAccessRole.java`
- `src/main/java/com/agent/editor/agent/v2/tool/memory/MemoryToolAccessPolicy.java`

Keep ordering stable and deduplicate appended tools.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest test`

Expected: PASS

### Task 2: Migrate orchestrators and configuration to the new policy

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReActAgentOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/planning/PlanningThenExecutionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/agent/v2/reflexion/ReflexionOrchestrator.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Delete: `src/main/java/com/agent/editor/agent/v2/tool/memory/MainAgentMemoryToolAccess.java`

**Step 1: Write the failing integration-adjacent tests**

Update or add assertions in existing orchestrator/config tests so they expect:

- orchestrators to be constructed with `ExecutionToolAccessPolicy`
- application context to contain the new policy beans

**Step 2: Run tests to verify they fail**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SingleAgentOrchestratorTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest test`

Expected: FAIL until constructors and bean wiring are updated

**Step 3: Write minimal implementation**

Update orchestrators to call:

- `executionToolAccessPolicy.allowedTools(document, ExecutionToolAccessRole.MAIN_WRITE)`
- `executionToolAccessPolicy.allowedTools(document, ExecutionToolAccessRole.REVIEW)` only if a path truly needs composed review access; otherwise keep direct document policy usage

Register the new beans in `TaskOrchestratorConfig`.

**Step 4: Run tests to verify they pass**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentV2ConfigurationSplitTest,SingleAgentOrchestratorTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest test`

Expected: PASS

### Task 3: Clean up old access helper references

**Files:**
- Delete: `src/test/java/com/agent/editor/agent/v2/tool/memory/MainAgentMemoryToolAccessTest.java`
- Update imports/usages across `src/main/java` and `src/test/java`

**Step 1: Write the failing cleanup assertion**

Add or update tests to ensure the replacement policy is the new source of truth and no tests rely on the old helper.

**Step 2: Run tests to verify cleanup gaps**

Run: `rg -n "MainAgentMemoryToolAccess" src/main/java src/test/java`

Expected: remaining references indicate cleanup work still needed

**Step 3: Remove the old helper and update references**

Replace the old helper test with targeted tests for `MemoryToolAccessPolicy`.

**Step 4: Verify references are gone**

Run: `rg -n "MainAgentMemoryToolAccess" src/main/java src/test/java`

Expected: no matches

### Task 4: Verify the focused regression surface

**Files:**
- Modify: all files above

**Step 1: Run the focused verification suite**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MemoryToolAccessPolicyTest,ExecutionToolAccessPolicyTest,AgentV2ConfigurationSplitTest,SingleAgentOrchestratorTest,PlanningThenExecutionOrchestratorTest,ReflexionOrchestratorTest test`

Expected: PASS

**Step 2: Review scope**

Run: `git diff --stat`

Expected: diff is limited to tool access policies, orchestrators, wiring, and tests

**Step 3: Report verification evidence**

Include the exact verification command and result in the completion note.

# Agent Package Flattening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the deprecated agent v1 stack and flatten the current agent runtime from `com.agent.editor.agent.v2` to `com.agent.editor.agent`, while also deleting versioned API naming and compatibility entrypoints.

**Architecture:** This migration keeps the current runtime behavior but removes historical version layering. First move source and test packages with `mv`, then repair package declarations/imports, then collapse controllers/websocket/frontend paths to final names, and finally remove v1/legacy leftovers and verify the repository is clean.

**Tech Stack:** Java 17, Spring Boot, Maven, JUnit 5, Mockito, WebSocket, server-rendered HTML/JS

---

### Task 1: Move Runtime Source And Test Packages

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2`
- Modify: `src/test/java/com/agent/editor/agent/v2`
- Delete: `src/main/java/com/agent/editor/agent/v1`
- Test: `src/test/java/com/agent/editor/agent`

**Step 1: Write the failing test**

Add a temporary migration guard test in `src/test/java/com/agent/editor/config/AgentPackageMigrationGuardTest.java` that fails if any class still imports `com.agent.editor.agent.v2` or `com.agent.editor.agent.v1`.

```java
@Test
void shouldNotReferenceVersionedAgentPackages() throws Exception {
    String sources = Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .map(path -> Files.readString(path))
            .collect(Collectors.joining("\n"));
    assertFalse(sources.contains("com.agent.editor.agent.v2"));
    assertFalse(sources.contains("com.agent.editor.agent.v1"));
}
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentPackageMigrationGuardTest test`
Expected: FAIL because the codebase still contains `agent.v1` and `agent.v2` references.

**Step 3: Write minimal implementation**

Use `mv` to flatten the directories before import repair:

```bash
mv src/main/java/com/agent/editor/agent/v2/core src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/event src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/mapper src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/memory src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/model src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/planning src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/react src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/reflexion src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/supervisor src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/task src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/tool src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/trace src/main/java/com/agent/editor/agent/
mv src/main/java/com/agent/editor/agent/v2/util src/main/java/com/agent/editor/agent/
mv src/test/java/com/agent/editor/agent/v2/* src/test/java/com/agent/editor/agent/
rm -rf src/main/java/com/agent/editor/agent/v2 src/main/java/com/agent/editor/agent/v1 src/test/java/com/agent/editor/agent/v2
```

Then batch-rewrite package declarations/imports from `com.agent.editor.agent.v2` to `com.agent.editor.agent`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentPackageMigrationGuardTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/agent src/test/java/com/agent/editor/agent src/test/java/com/agent/editor/config/AgentPackageMigrationGuardTest.java
git commit -m "refactor: flatten agent runtime packages"
```

### Task 2: Repair Spring Wiring And Application Imports

**Files:**
- Modify: `src/main/java/com/agent/editor/config/PlanningAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ReactAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ReflexionAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/SupervisorAgentConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Modify: `src/main/java/com/agent/editor/config/ToolConfig.java`
- Modify: `src/main/java/com/agent/editor/config/TraceConfig.java`
- Modify: `src/main/java/com/agent/editor/config/LangChainConfig.java`
- Modify: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Modify: `src/main/java/com/agent/editor/service/TaskQueryService.java`
- Modify: `src/main/java/com/agent/editor/service/SessionMemoryQueryService.java`
- Modify: `src/main/java/com/agent/editor/service/StructuredDocumentService.java`
- Modify: `src/main/java/com/agent/editor/service/LongTermMemoryRetrievalService.java`
- Modify: `src/main/java/com/agent/editor/service/LongTermMemoryWriteService.java`
- Modify: `src/main/java/com/agent/editor/repository/LongTermMemoryRepository.java`
- Modify: `src/main/java/com/agent/editor/repository/MilvusLongTermMemoryRepository.java`
- Test: `src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java`

**Step 1: Write the failing test**

Rename `src/test/java/com/agent/editor/config/AgentV2ConfigurationSplitTest.java` to `src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java`, update imports/class name, and make it assert beans from `com.agent.editor.agent...` packages.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentConfigurationSplitTest test`
Expected: FAIL because config classes still point at old package names or stale bean types.

**Step 3: Write minimal implementation**

Update all config/service/repository imports to the flattened package names and fix any bean type references left behind by the move.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentConfigurationSplitTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config src/main/java/com/agent/editor/service src/main/java/com/agent/editor/repository src/test/java/com/agent/editor/config/AgentConfigurationSplitTest.java
git commit -m "refactor: rewire app imports to flattened agent packages"
```

### Task 3: Remove Legacy Service And Versioned Controller Naming

**Files:**
- Delete: `src/main/java/com/agent/editor/service/LegacyAgentService.java`
- Delete: `src/test/java/com/agent/editor/service/LegacyAgentServiceTest.java`
- Delete: `src/test/java/com/agent/editor/agent/LegacyAgentTypesTest.java`
- Modify: `src/main/java/com/agent/editor/controller/AgentController.java`
- Modify: `src/main/java/com/agent/editor/controller/LongTermMemoryController.java`
- Modify: `src/main/java/com/agent/editor/controller/TraceController.java`
- Delete or Move: `src/main/java/com/agent/editor/controller/AgentV2Controller.java`
- Modify: `src/test/java/com/agent/editor/controller/AgentControllerTest.java`
- Modify: `src/test/java/com/agent/editor/controller/AgentV2ControllerTest.java`
- Modify: `src/test/java/com/agent/editor/controller/TraceControllerTest.java`

**Step 1: Write the failing test**

Update controller tests to target only final routes:
- `/api/agent`
- `/api/memory`
- `/api/agent/task/{taskId}/trace`

Also rename `AgentV2ControllerTest` to `AgentControllerApiTest` or merge it into the final `AgentControllerTest`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentControllerTest,AgentControllerApiTest,TraceControllerTest test`
Expected: FAIL because controllers still expose `/api/v1/*` or `/api/v2/*` routes and versioned class names.

**Step 3: Write minimal implementation**

- merge or rename `AgentV2Controller` into the final controller surface
- remove `LegacyAgentService` and any remaining references
- rename controller classes and request mappings to final non-versioned paths

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentControllerTest,AgentControllerApiTest,TraceControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/controller src/main/java/com/agent/editor/service src/test/java/com/agent/editor/controller src/test/java/com/agent/editor/service
git commit -m "refactor: remove legacy agent entrypoints and versioned controllers"
```

### Task 4: Rename WebSocket And Event Entry Points

**Files:**
- Modify or Move: `src/main/java/com/agent/editor/websocket/AgentV2WebSocketHandler.java`
- Modify: `src/main/java/com/agent/editor/websocket/AgentWebSocketHandler.java`
- Modify: `src/main/java/com/agent/editor/websocket/WebSocketService.java`
- Modify: `src/main/java/com/agent/editor/config/WebSocketConfig.java`
- Modify: `src/test/java/com/agent/editor/websocket/AgentV2WebSocketHandlerTest.java`
- Modify: `src/test/java/com/agent/editor/websocket/WebSocketServiceV2Test.java`

**Step 1: Write the failing test**

Rename websocket tests to final names and update expectations so no bean/class/path contains `V2`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentWebSocketHandlerTest,WebSocketServiceTest test`
Expected: FAIL because websocket handlers/services still use `V2` naming or stale imports.

**Step 3: Write minimal implementation**

- rename `AgentV2WebSocketHandler` to its final name
- rename `WebSocketServiceV2Test` and related references
- update `WebSocketConfig` and any handler registration paths accordingly

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentWebSocketHandlerTest,WebSocketServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/websocket src/main/java/com/agent/editor/config/WebSocketConfig.java src/test/java/com/agent/editor/websocket
git commit -m "refactor: remove v2 naming from websocket entrypoints"
```

### Task 5: Update Frontend Calls And Final Repository Guard

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`
- Modify: `src/test/java/com/agent/editor/controller/AgentControllerTest.java`
- Modify: `src/test/java/com/agent/editor/controller/TraceControllerTest.java`
- Test: `src/test/java/com/agent/editor/config/AgentPackageMigrationGuardTest.java`

**Step 1: Write the failing test**

Extend `AgentPackageMigrationGuardTest` so it also fails if the repository still contains:
- `/api/v2/`
- `AgentV2`
- `LegacyAgentService`

```java
assertFalse(allText.contains("/api/v2/"));
assertFalse(allText.contains("AgentV2"));
assertFalse(allText.contains("LegacyAgentService"));
```

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentPackageMigrationGuardTest test`
Expected: FAIL because the HTML template and remaining tests/controllers still use versioned names and paths.

**Step 3: Write minimal implementation**

- update `src/main/resources/templates/index.html` to call `/api/agent/*` and `/api/memory/*`
- update remaining controller tests to final paths only
- clean remaining `AgentV2` / `/api/v2` / `LegacyAgentService` references

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentPackageMigrationGuardTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller src/test/java/com/agent/editor/config/AgentPackageMigrationGuardTest.java
git commit -m "refactor: remove versioned agent api references"
```

### Task 6: Final Verification Sweep

**Files:**
- Verify: `src/main/java/com/agent/editor/agent`
- Verify: `src/test/java/com/agent/editor/agent`
- Verify: `src/main/java/com/agent/editor/controller`
- Verify: `src/main/resources/templates/index.html`

**Step 1: Write the failing test**

No new test file. Reuse the repository guard and the existing runtime/controller/websocket tests assembled by earlier tasks.

**Step 2: Run test to verify it fails**

Before cleanup is complete, run the full targeted suite to expose any remaining stale import or path:

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=AgentPackageMigrationGuardTest,AgentConfigurationSplitTest,AgentControllerTest,AgentControllerApiTest,LongTermMemoryControllerTest,TraceControllerTest,AgentWebSocketHandlerTest,WebSocketServiceTest,TaskApplicationServiceTest test`
Expected: FAIL until all rename/move work is complete.

**Step 3: Write minimal implementation**

Fix the remaining stale imports, package statements, bean references, or route assertions exposed by the verification run.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test`
Expected: PASS

Then run a final textual sweep:

Run: `rg -n 'com\\.agent\\.editor\\.agent\\.v1|com\\.agent\\.editor\\.agent\\.v2|AgentV2|LegacyAgentService|/api/v2/' src/main/java src/test/java src/main/resources`
Expected: no matches

**Step 5: Commit**

```bash
git add src/main/java src/test/java src/main/resources
git commit -m "refactor: finalize agent package flattening"
```

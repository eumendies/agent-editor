# React Document Write Default Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Strengthen the ReAct v2 system prompt so writing requests default to `editDocument` and overwrite the full document when no target position is given.

**Architecture:** Keep the change prompt-only inside `ReactAgentDefinition`. Validate the new rules with a focused unit test that checks the generated system prompt content through the model request path. Do not modify runtime flow or the commented `UserMessage` line.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, LangChain4j

---

### Task 1: Lock behavior with a failing test

**Files:**
- Modify: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`

**Step 1: Write the failing test**

Add a test that executes `ReactAgentDefinition.decide(...)`, captures the outgoing `SystemMessage`, and asserts it includes:
- writing requests must call `editDocument`
- no position means overwrite the full document
- direct chat replies are only for non-editing requests

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentDefinitionTest test`

Expected: FAIL because the current prompt text does not include the new rules.

### Task 2: Update the prompt minimally

**Files:**
- Modify: `src/main/java/com/agent/editor/agent/v2/react/ReactAgentDefinition.java`

**Step 1: Write minimal implementation**

Update `buildSystemPrompt()` only. Add explicit instructions that writing-style requests should edit the current document, default to full overwrite via `editDocument` when no location is specified, and keep direct text replies for non-editing requests only.

**Step 2: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentDefinitionTest test`

Expected: PASS

### Task 3: Re-verify targeted scope

**Files:**
- Test: `src/test/java/com/agent/editor/agent/v2/react/ReactAgentDefinitionTest.java`

**Step 1: Run related tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=ReactAgentDefinitionTest,ExecutionMemoryChatMessageMapperTest test`

Expected: PASS

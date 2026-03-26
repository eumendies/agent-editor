# Real Embedding Bad Case Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a manual integration test that embeds a query and candidate strings with the real embedding model, then ranks them by cosine similarity for RAG bad case exploration.

**Architecture:** Keep all behavior inside a dedicated JUnit test so production code stays unchanged. Use `KnowledgeEmbeddingService` with the real Spring-configured embedding bean and guard execution behind a JVM flag so default test runs do not call the remote embedding API.

**Tech Stack:** Java 17, JUnit 5, Spring Boot Test, LangChain4j OpenAI embedding model.

---

### Task 1: Add the bad case integration test

**Files:**
- Create: `src/test/java/com/agent/editor/service/KnowledgeEmbeddingBadCaseTest.java`
- Test: `src/test/java/com/agent/editor/service/KnowledgeEmbeddingBadCaseTest.java`

**Step 1: Write the failing test**

Create a test that:
- defines a `query`
- defines several `candidates`
- uses `KnowledgeEmbeddingService` to embed them
- ranks candidates by cosine similarity
- asserts the result list size matches the input size
- asserts the scores are sorted descending

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=KnowledgeEmbeddingBadCaseTest test`

Expected: compile or runtime failure before helper logic is complete, or test skipped before manual enablement is added.

**Step 3: Write minimal implementation**

Implement inside the test class:
- manual execution gate via `realEmbeddingTest`
- query/candidate embedding
- cosine similarity helper
- result ranking and debug output

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=KnowledgeEmbeddingBadCaseTest test`

Expected: test is skipped by default.

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -Dtest=KnowledgeEmbeddingBadCaseTest -DrealEmbeddingTest=true test`

Expected: test passes and prints ranked similarity results, assuming the embedding API is reachable and credentials are configured.

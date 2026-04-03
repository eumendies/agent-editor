# Document Service Seeded Resources Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move `DocumentService` seeded document bodies out of Java source and load them from classpath resources while preserving the existing seeded document behavior.

**Architecture:** Keep seed metadata in `DocumentService`, store document bodies in `src/main/resources/documents/`, and initialize the in-memory document map by reading each resource as UTF-8 text. Use a `ResourceLoader`-based constructor so Spring and tests can share the same loading path.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Maven

---

### Task 1: Add a failing resource-loading test

**Files:**
- Modify: `src/test/java/com/agent/editor/service/DocumentServiceTest.java`

**Step 1: Write the failing test**

Add a test that constructs `DocumentService` with a custom `ResourceLoader` and asserts the seeded documents use the bytes returned by that loader instead of constructor-embedded strings.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentServiceTest test`

Expected: FAIL because `DocumentService` does not yet support resource-backed initialization.

### Task 2: Move seeded content into resources and load it

**Files:**
- Create: `src/main/resources/documents/doc-001.md`
- Create: `src/main/resources/documents/doc-002.txt`
- Modify: `src/main/java/com/agent/editor/service/DocumentService.java`

**Step 1: Write minimal implementation**

Add seed definitions that map `docId`, `title`, and resource path. Replace the hard-coded content block with resource reads. Fail fast if a required resource is missing or unreadable.

**Step 2: Run focused tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentServiceTest test`

Expected: PASS

### Task 3: Verify compatibility with existing callers

**Files:**
- Modify: `src/test/java/com/agent/editor/service/TaskApplicationEventQueryTest.java`

**Step 1: Update direct instantiation if needed**

Keep test construction aligned with the final `DocumentService` constructor shape.

**Step 2: Run the affected tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=DocumentServiceTest,TaskApplicationEventQueryTest test`

Expected: PASS

# Milvus Hybrid Range Filter Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add configurable Milvus-native range-filter parameters for the dense and sparse branches of hybrid retrieval.

**Architecture:** Extend `MilvusProperties` with nested dense/sparse hybrid range settings, wire default values from `application.yml`, and apply those values directly when constructing the two `AnnSearchReq` instances inside `MilvusKnowledgeChunkRepository`.

**Tech Stack:** Java 17, Spring Boot configuration properties, JUnit 5, Mockito

---

### Task 1: Lock Down Hybrid Request Parameters with a Failing Test

**Files:**
- Modify: `src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java`
- Test: `src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java`

**Step 1: Write the failing test**

Add a test that constructs `MilvusProperties` with dense/sparse hybrid settings and asserts:

- dense request has the configured `radius`
- dense request has the configured `rangeFilter`
- sparse request has the configured `radius`
- sparse request has the configured `rangeFilter`

using the captured `HybridSearchReq`.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest#shouldApplyConfiguredHybridRangeFilters test`

Expected: FAIL because `MilvusProperties` does not yet expose these settings and `MilvusKnowledgeChunkRepository` does not apply them.

**Step 3: Write minimal implementation**

Add nested configuration classes under `MilvusProperties` and use them in the repository when building each `AnnSearchReq`.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest#shouldApplyConfiguredHybridRangeFilters test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/agent/editor/config/MilvusProperties.java \
        src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java \
        src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java
git commit -m "feat: configure milvus hybrid range filters"
```

### Task 2: Add Defaults and Run Focused Regression

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java`
- Verify: files from Task 1

**Step 1: Write the failing assertion**

Extend repository test coverage if needed to ensure existing hybrid request shape still holds after adding the new parameters.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest test`

Expected: FAIL if defaults or request assertions are incomplete.

**Step 3: Write minimal implementation**

Add default dense/sparse `radius` and `range-filter` values to `application.yml`.

**Step 4: Run focused regression tests**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=MilvusKnowledgeChunkRepositoryTest,KnowledgeRetrievalServiceTest,KnowledgeRetrievalServiceVectorTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/resources/application.yml \
        src/main/java/com/agent/editor/config/MilvusProperties.java \
        src/main/java/com/agent/editor/repository/MilvusKnowledgeChunkRepository.java \
        src/test/java/com/agent/editor/service/MilvusKnowledgeChunkRepositoryTest.java \
        docs/plans/2026-03-31-milvus-hybrid-range-filter.md
git commit -m "test: cover milvus hybrid range filter config"
```

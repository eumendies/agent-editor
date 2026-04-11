# README Refresh Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rewrite `README.md` into a current, GitHub-style project homepage centered on the repository's actual agent architecture and behavior.

**Architecture:** The rewrite will treat current source code as the only source of truth, then restructure the README around agent modes, execution flow, code map, memory/retrieval, and a short quick-start section. Outdated package names, routes, and unverifiable claims will be removed rather than preserved.

**Tech Stack:** Markdown, Spring Boot 3, Java 17, LangChain4j, Milvus, Maven

---

### Task 1: Confirm factual anchors for the README

**Files:**
- Review: `src/main/java/com/agent/editor/model/AgentMode.java`
- Review: `src/main/java/com/agent/editor/controller/AgentController.java`
- Review: `src/main/java/com/agent/editor/controller/PageController.java`
- Review: `src/main/java/com/agent/editor/service/TaskApplicationService.java`
- Review: `src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`
- Review: `src/main/resources/application.yml`
- Review: `.env.example`

**Step 1: Re-read the source-of-truth files**

Run: `sed -n '1,220p' src/main/java/com/agent/editor/model/AgentMode.java`
Run: `sed -n '1,260p' src/main/java/com/agent/editor/controller/AgentController.java`
Run: `sed -n '1,120p' src/main/java/com/agent/editor/controller/PageController.java`

**Step 2: Re-check orchestration and runtime flow**

Run: `sed -n '1,320p' src/main/java/com/agent/editor/service/TaskApplicationService.java`
Run: `sed -n '1,280p' src/main/java/com/agent/editor/config/TaskOrchestratorConfig.java`

**Step 3: Re-check startup and config facts**

Run: `sed -n '1,220p' src/main/resources/application.yml`
Run: `sed -n '1,120p' .env.example`

**Step 4: Capture the facts to preserve**

Expected:
- Four modes: `REACT`, `PLANNING`, `SUPERVISOR`, `REFLEXION`
- Agent task endpoint prefix: `/api/agent`
- Async task submission via `executeAsync`
- Page entry points: `/` and `/editor`

### Task 2: Rewrite the README structure and content

**Files:**
- Modify: `README.md`
- Reference: `docs/plans/2026-04-11-readme-refresh-design.md`

**Step 1: Replace outdated framing with agent-first structure**

Edit `README.md` to include:
- title and one-line summary
- why this project
- highlights
- agent modes
- execution flow
- code map
- memory and retrieval
- quick start
- entry points

**Step 2: Remove stale claims**

Delete or rewrite any content referring to:
- `agent.v2`
- legacy/v1 split claims
- `/api/v1/agent/...`
- unsupported or unverified trace/demo assertions

**Step 3: Keep the README concise**

Expected:
- main page reads like a GitHub repository homepage
- agent-related material remains the center of gravity
- setup and API sections stay short

### Task 3: Verify the rewritten README against the codebase

**Files:**
- Verify: `README.md`

**Step 1: Read the rewritten README**

Run: `sed -n '1,260p' README.md`

**Step 2: Check for stale identifiers**

Run: `rg -n "agent\\.v2|/api/v1/agent|legacy|v1" README.md`
Expected: no matches that misdescribe current architecture

**Step 3: Check route and mode references**

Run: `rg -n "/api/agent|REACT|PLANNING|SUPERVISOR|REFLEXION|/editor|/api/memory/profiles" README.md`
Expected: README references current entry points and all four supported modes

**Step 4: Review git diff**

Run: `git diff -- README.md docs/plans/2026-04-11-readme-refresh-design.md docs/plans/2026-04-11-readme-refresh.md`
Expected: diff shows only the planned documentation rewrite

# User Profile Memory UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an explicit user profile memory management panel to the demo workbench so users can view, create, edit, and delete `USER_PROFILE` memories through the existing `/api/v2/memory/profiles` endpoints.

**Architecture:** Keep the existing single-template workbench architecture. Extend `index.html` with one additional panel, a small set of native-JS state variables, and fetch helpers that treat the backend as the source of truth by reloading the profile list after each successful mutation.

**Tech Stack:** Thymeleaf template, native browser DOM APIs, Fetch API, Spring Boot existing memory controller/service.

---

### Task 1: Add focused UI regression expectations

**Files:**
- Modify: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`
- Review: `src/main/java/com/agent/editor/controller/LongTermMemoryController.java`
- Review: `src/main/java/com/agent/editor/dto/UserProfileMemoryRequest.java`
- Review: `src/main/java/com/agent/editor/dto/UserProfileMemoryResponse.java`

**Step 1: Write the failing test**

Add or refine controller tests so they clearly lock down the existing profile endpoints used by the UI:

- list returns `200` and a JSON array of profiles
- create accepts `summary`
- update accepts `summary`
- delete returns `200`

Use focused assertions on route shape and JSON fields rather than broad integration behavior.

**Step 2: Run test to verify it fails**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest test`

Expected: at least one assertion fails if the tightened expectation is not already covered.

**Step 3: Write minimal implementation**

If the existing controller already satisfies the behavior, adjust only the test coverage and do not change production code.

**Step 4: Run test to verify it passes**

Run: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java
git commit -m "test: lock user profile memory api"
```

### Task 2: Add the user profile memory panel markup and styles

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Write the failing test**

There is no dedicated HTML test harness here, so create a manual verification target by first editing the template structure only after documenting the expected DOM IDs in code comments near the new section.

Expected DOM IDs:

- `userProfilePanel`
- `userProfileStatus`
- `newUserProfileInput`
- `addUserProfileBtn`
- `userProfileList`

**Step 2: Run verification to confirm the current page lacks the panel**

Run:

```bash
rg -n "userProfilePanel|newUserProfileInput|userProfileList" src/main/resources/templates/index.html
```

Expected: no matches

**Step 3: Write minimal implementation**

In `index.html`:

- extend the right-side chat layout with a new `User Profile Memory` section below the composer
- add concise explanatory copy in Chinese
- add textarea for new profile creation
- add list container and empty state container
- add CSS for the panel, list cards, inline edit state, and status messages

Keep the visual language consistent with the existing page.

**Step 4: Run verification to confirm the markup exists**

Run:

```bash
rg -n "userProfilePanel|newUserProfileInput|userProfileList" src/main/resources/templates/index.html
```

Expected: matches for all new IDs

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: add user profile memory panel layout"
```

### Task 3: Implement profile loading and rendering in native JS

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Write the failing test**

Use a red step based on script verification targets:

- new functions do not yet exist: `loadUserProfiles`, `renderUserProfiles`, `renderUserProfileStatus`
- no fetch call yet targets `/api/v2/memory/profiles`

Run:

```bash
rg -n "loadUserProfiles|renderUserProfiles|renderUserProfileStatus|/api/v2/memory/profiles" src/main/resources/templates/index.html
```

Expected: no matches or incomplete matches

**Step 2: Run verification to confirm it fails**

Use the command above and confirm the required functions/endpoints are absent.

**Step 3: Write minimal implementation**

Add native-JS state and helpers to `index.html`:

- `let userProfiles = []`
- `let userProfilesLoading = false`
- `let userProfileStatus = ""`
- `loadUserProfiles()` to fetch list data
- `renderUserProfiles()` to render empty state and cards
- `renderUserProfileStatus(type, message)` for loading/success/error
- call `loadUserProfiles()` during page initialization

Treat the backend response as the source of truth.

**Step 4: Run verification to confirm it exists**

Run:

```bash
rg -n "loadUserProfiles|renderUserProfiles|renderUserProfileStatus|/api/v2/memory/profiles" src/main/resources/templates/index.html
```

Expected: all functions and endpoint references found

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: load user profile memories in workbench"
```

### Task 4: Implement create, inline edit, and delete actions

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Step 1: Write the failing test**

Define explicit verification targets for mutation helpers:

- `createUserProfile()`
- `startEditingUserProfile(memoryId)`
- `saveUserProfile(memoryId)`
- `cancelEditingUserProfile()`
- `deleteUserProfile(memoryId)`

Run:

```bash
rg -n "createUserProfile|startEditingUserProfile|saveUserProfile|cancelEditingUserProfile|deleteUserProfile" src/main/resources/templates/index.html
```

Expected: no matches

**Step 2: Run verification to confirm it fails**

Use the command above and confirm the mutation helpers are absent.

**Step 3: Write minimal implementation**

Add inline editing behavior:

- `POST` for create using textarea content
- `PUT` for save when editing an existing card
- `DELETE` with a lightweight confirmation step
- reload list with `loadUserProfiles()` after each successful mutation
- preserve user input on failures where reasonable

Add Chinese comments around any non-obvious branching, especially edit-state transitions and refresh-after-mutation behavior.

**Step 4: Run verification to confirm it exists**

Run:

```bash
rg -n "createUserProfile|startEditingUserProfile|saveUserProfile|cancelEditingUserProfile|deleteUserProfile" src/main/resources/templates/index.html
```

Expected: all helpers present

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: add user profile memory editing actions"
```

### Task 5: End-to-end verification and cleanup

**Files:**
- Review: `src/main/resources/templates/index.html`
- Review: `src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java`

**Step 1: Run focused automated verification**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -Dtest=LongTermMemoryControllerTest test
```

Expected: PASS

**Step 2: Run full automated verification**

Run:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn test
```

Expected: PASS

**Step 3: Perform manual browser verification**

Run the app and validate:

1. The right-side panel shows existing user profiles after page load.
2. Adding a new profile updates the list.
3. Editing a profile updates the list.
4. Deleting a profile removes it from the list.
5. Error messaging stays inside the panel and does not break chat/document flows.

**Step 4: Review for scope discipline**

Confirm the change does not add:

- document decision management UI
- new backend endpoints
- new frontend framework dependencies
- unrelated layout rewrites

**Step 5: Commit**

```bash
git add src/main/resources/templates/index.html src/test/java/com/agent/editor/controller/LongTermMemoryControllerTest.java
git commit -m "test: verify user profile memory ui integration"
```

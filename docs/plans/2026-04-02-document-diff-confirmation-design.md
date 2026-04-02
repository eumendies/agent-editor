# Document Diff Confirmation Design

## Goal

Change the document update flow so agent-generated content is not written back immediately. After a task completes, the UI should show a pending diff, keep the editor on the currently saved document content, and only persist the change after the user explicitly confirms it. If the user discards the change, the candidate update must be removed.

## Scope

In scope:

- stop automatic document writes when an agent task finishes
- persist one pending document change per document so refresh does not lose it
- expose APIs to read, apply, and discard the pending change
- update the demo page so the diff panel becomes the confirmation surface
- keep applied diff history available after confirmation

Out of scope:

- multi-version pending queues per document
- optimistic merge/rebase against concurrent edits
- replacing the current simple line-based diff algorithm

## Current Problem

`TaskApplicationService` currently writes `result.getFinalContent()` directly into `DocumentService` once the orchestrator completes, then records a diff. That makes the diff panel a retrospective view of a change that is already applied. This conflicts with the desired review-first flow because:

- the editor content changes before the user approves it
- refresh cannot distinguish pending candidate content from committed content
- discard is impossible because the source of truth has already been overwritten

## Recommended Approach

Introduce a dedicated pending-change model and service instead of expanding `DocumentService`.

Why this approach:

- it preserves the existing responsibility boundary where `DocumentService` owns saved documents
- it keeps task orchestration, pending review, and final apply/discard actions explicit
- it supports refresh-safe confirmation without introducing new task status enums

## Data Model

Add a new DTO and service-level store for a pending change keyed by `documentId`.

Suggested fields:

- `documentId`
- `taskId`
- `originalContent`
- `proposedContent`
- `diffHtml`
- `createdAt`

Rules:

- only the latest pending change for a document is kept
- a later completed task for the same document replaces the earlier pending change
- applying a pending change updates the document, records an applied diff history entry, and removes the pending change
- discarding a pending change only removes the pending record

## Backend Flow

### Task Completion

When the orchestrator returns `finalContent`:

- do not call `documentService.updateDocument(...)`
- generate diff content from the original snapshot and final content
- persist a pending change via a dedicated service
- mark the task as completed so polling can stop normally

### Pending Change Queries

Expose a read endpoint that returns the pending change for a document if present. This endpoint is the UI source of truth for the confirmation panel after task completion, page refresh, or document switching.

### Apply / Discard

Add explicit apply and discard operations:

- apply: load pending change, update document content, record diff history, delete pending change
- discard: delete pending change without touching the document

If no pending change exists, return a non-success status that the UI can handle cleanly.

## API Shape

Keep review-oriented endpoints under the diff namespace because they are tightly coupled to the diff panel.

Suggested endpoints:

- `GET /api/v1/diff/document/{documentId}/pending`
- `POST /api/v1/diff/document/{documentId}/apply`
- `DELETE /api/v1/diff/document/{documentId}/pending`

Existing diff-history endpoints remain for already applied history. The pending endpoint should be treated separately in the UI so “candidate change” and “history” are not mixed together.

## Frontend Behavior

The editor continues to show the saved document content until confirmation.

Task lifecycle:

1. user submits a task
2. the app still auto-saves unsaved editor content before submission
3. task completes
4. the app loads pending diff data instead of refreshing the editor
5. the diff panel shows:
   - the pending diff
   - review status copy
   - `确认应用`
   - `放弃修改`
6. on apply, refresh the editor from the saved document and clear the pending panel
7. on discard, keep the editor unchanged and clear the pending panel

Refresh and document switching:

- the editor always reads committed content from `/api/v1/documents/{id}`
- the diff panel always reads pending review state from `/pending`
- this makes the UI stable across reloads

## Error Handling

- applying without a pending change should not mutate the document
- discarding without a pending change should be a harmless handled error
- if apply fails after reading the pending change, the pending record should remain so the user can retry
- the UI should show a clear error message and keep the pending diff visible when apply fails

## Testing

### Backend

- `TaskApplicationServiceTest`: completed tasks create pending changes instead of updating documents immediately
- pending-change service tests: create, replace, read, apply, discard
- controller tests for pending read/apply/discard endpoints and not-found behavior

### Frontend

- template test checks for pending-diff loading and review action hooks
- task finalization no longer refreshes the editor immediately
- apply action refreshes the editor after success
- discard action leaves the editor content unchanged

## Open Tradeoff

This design stores only one pending change per document. That is intentional. The current app is single-document, single-user oriented, and a queue of pending proposals would complicate both the UI and the apply semantics. If parallel candidate revisions become a product need later, that should be introduced as a separate versioned review model instead of being inferred from task history.

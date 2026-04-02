# Demo Page Editor-Chat Workbench Design

## Goal

Reshape the existing orchestration demo page into a practical editing workbench: the left side becomes the primary document editor, and the right side becomes a unified chat panel for instruction input, runtime events, and final agent feedback.

## User Intent

The current page over-emphasizes demo narration and mode explanation. The target workflow is different:

- remove the large introductory hero area
- stop splitting task input and event stream into separate regions
- remove the mode explanation card
- allow direct manual editing of the document content

This iteration should make the page feel like an editor with an attached agent conversation, not a presentation dashboard.

## Scope

In scope:

- remove the top hero/intro section
- remove the `Mode Lens` card
- replace the current main stage with a two-column layout
- make the document content editable
- add an explicit save action backed by `PUT /api/v1/documents/{id}`
- merge `Scenario Bar` and `Mode Event Stream` into one chat-oriented interaction panel
- keep the latest diff view
- keep knowledge upload as a separate lower section

Out of scope:

- redesigning the backend orchestration APIs
- introducing rich-text editing
- multi-document tabs or local draft persistence
- replacing the current diff algorithm
- changing the knowledge upload flow

## Layout

The page should use a persistent two-column desktop layout, with a mobile fallback that stacks vertically.

### Left: Document Workspace

The left column is the primary working area.

It contains:

- a compact header with document selector, save button, and refresh button
- a single large editable textarea for the document body
- a small save status area for success/error feedback
- the existing diff panel below the editor

This replaces the old `Original Snapshot` and `Current Result` comparison cards. The user should edit only one source of truth on screen.

### Right: Chat Workspace

The right column combines the previous input controls and runtime timeline into a single panel.

It contains:

- a compact top bar with current mode selector, websocket status, and clear-chat action
- a scrollable message list that shows user instructions, runtime system events, streaming agent output, and final result summaries
- a bottom composer with instruction textarea and the existing four run buttons
- a compact trace summary area attached to the bottom of the same panel instead of a separate large side card

This preserves operational control while removing the artificial split between “scenario setup” and “event stream”.

## Interaction Design

### Document Editing

When the page loads or the selected document changes, the editor should be populated from `GET /api/v1/documents/{id}`.

The editor is fully editable. Manual changes stay local until the user clicks save.

Saving should:

1. send `PUT /api/v1/documents/{id}?content=...`
2. update the save status area
3. refresh the current editor value from the response
4. refresh the diff area so the page stays consistent with server state

### Agent Run Flow

The instruction composer remains separate from the document editor.

Before submitting an agent task, the page should ensure the current editor content is saved first. This avoids a mismatch where the agent reads stale server-side content while the user sees unsaved local edits.

The run flow should be:

1. if the document editor has unsaved changes, save the document first
2. append the instruction as a user message in the chat list
3. submit `/api/v2/agent/execute`
4. stream runtime events into the same message list
5. when the task completes, refresh the document editor from the server and refresh diff/trace data

## Chat Message Model

The right-side panel should visually treat execution as a conversation with structured system inserts.

Message types:

- user message: the submitted instruction
- assistant streaming message: merged from `TEXT_STREAM_*`
- system event message: tool calls, plan creation, worker dispatch/completion, task state changes
- assistant final message: terminal summary or final text when available

The previous event cards can keep their current semantics internally, but the visual shell should read as message bubbles/cards in one feed.

## Data and State Rules

- the editor textarea becomes the single on-screen document source of truth
- `originalSnapshot` may still be kept in script state for diff/history behavior, but it should no longer render as a dedicated card
- task completion should overwrite the editor with the latest persisted document content
- switching documents should reset editor, diff, trace summary, and chat feed
- clearing chat should only clear execution/chat state, not the editor content

## Visual Direction

Keep the existing dark glassmorphism style tokens where possible, but simplify hierarchy:

- less decorative marketing copy
- stronger editor emphasis
- calmer panel titles
- chat messages with clearer separation between user, assistant, and system activity

The page should feel more like an internal writing tool than a showcase deck.

## Error Handling

- save failures should show a visible inline status in the document workspace
- agent submission failures should render into the chat feed as error messages
- websocket disconnect should continue to show a compact status pill and reconnect behavior
- if diff or trace loading fails, those panels should keep a readable empty/error state without blocking editing

## Testing

The existing `DemoPageTemplateTest` should be updated to validate the new editor/chat workbench markers and the removed legacy copy.

This iteration does not require browser automation. Focused template assertions are sufficient because the behavior change stays inside the existing Thymeleaf page shell.

## Risks

- saving the document before every run adds an extra request, but it is necessary to keep the agent input consistent with the editor state
- consolidating multiple panels into one script-driven chat feed increases DOM coupling, so the implementation should rename and isolate the new ids clearly
- removing old demo copy may break brittle tests or assumptions if any downstream test depends on exact strings

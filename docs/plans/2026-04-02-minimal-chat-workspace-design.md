# Minimal Chat Workspace Design

## Goal

Replace the current over-engineered chat workspace with a minimal chat-style panel that looks and behaves like a messaging UI.

## Scope

In scope:

- remove the current grouped session presentation
- remove trace inspector, progress bar, mode summary block, clear-chat button, and multi-button run controls
- keep a simple message list with user, assistant, and system bubbles
- keep a single composer row with instruction input, mode dropdown, and send button
- continue streaming runtime events into the message list

Out of scope:

- changing backend agent APIs
- changing left-side document workspace
- changing diff or knowledge upload sections

## Layout

The right column should become three layers only:

1. compact top bar
2. scrollable message list
3. compact composer

### Top Bar

Keep only:

- websocket status
- message count

This bar should be visually light and should not compete with the message list.

### Message List

The message list should look like a chat app:

- user message bubble for submitted instructions
- assistant bubble for streamed response text
- system bubble for tool calls, planning events, worker dispatches, and runtime notices
- error bubble for submission/runtime failures

No grouping, no session cards, no trace card, no final-result card.

### Composer

The composer should contain:

- multiline textarea
- mode dropdown
- send button

This replaces the previous four per-mode run buttons. The send action uses the currently selected mode.

## Interaction

When the user clicks send:

1. save the document first if there are local edits
2. append the instruction as a user bubble
3. submit the task using the selected mode
4. stream runtime events into the same message list

## Testing

Update the template test to assert the new minimal chat anchors and the removal of grouped-session markup and trace inspector.

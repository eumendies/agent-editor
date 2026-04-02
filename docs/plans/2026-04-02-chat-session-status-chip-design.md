# Chat Session Status Chip Design

## Goal

Make each chat session header easier to scan by turning the current plain-text session status into a colored status chip.

## Scope

In scope:

- style only the session status item in the chat-session header
- support `Running`, `Completed`, and `Failed`
- keep the rest of the chat session card visually unchanged

Out of scope:

- recoloring the full session card
- recoloring the session border
- changing chat message styles

## Visual Approach

Use a compact pill-style status chip inside the existing session meta row:

- `Running`: cool accent / cyan tone
- `Completed`: green tone
- `Failed`: red tone

The chip should be visually stronger than the surrounding meta text but still secondary to the message content.

## Behavior

The existing session lifecycle remains unchanged. Only the status rendering changes:

- new sessions start as `Running`
- successful completion switches to `Completed`
- failed execution switches to `Failed`

The implementation should update both the status text and the chip class together so the DOM always matches the runtime state.

## Testing

Extend the template test to assert the new chip hooks are present:

- `chat-session-status`
- `setSessionStatus`
- status variant class names used by the template script

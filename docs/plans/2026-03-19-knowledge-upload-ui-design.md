# Knowledge Upload UI Design

## Goal

Add a minimal knowledge-base upload UI to the existing demo page so users can upload a Markdown or TXT file into the knowledge store without leaving the orchestration demo.

## Scope

This first version is intentionally small:

- add a new knowledge upload panel at the bottom of the existing demo page
- support choosing a file and entering a category
- submit to `POST /api/v1/knowledge/documents`
- show upload state and a single result card after completion
- show a readable error message when upload fails

Out of scope for this iteration:

- drag-and-drop upload
- upload history or document list
- progress bars for file transfer
- knowledge retrieval UI
- RAG ask/write UI

## Placement

The new UI should live below the existing orchestration stage and timeline sections.

Reasoning:

- it keeps the current agent demo flow visually intact
- it avoids crowding the existing scenario bar
- it still makes the knowledge feature visible on the same page

## Interaction Design

The panel should contain:

- a file input restricted to supported document types
- a category text input
- a single upload button
- a status area below the form

The upload interaction should behave like this:

1. user chooses a file and fills in category
2. clicking upload disables the button and shows an uploading state
3. frontend posts `multipart/form-data` to `/api/v1/knowledge/documents`
4. on success, a result card renders `fileName`, `category`, `status`, and `documentId`
5. on failure, an error card or inline error copy renders the response message

The form does not need to auto-clear after success in this iteration.

## Visual Approach

Reuse the current design language from `index.html`:

- same glassmorphism panel shell
- same spacing rhythm and border radius
- same muted helper copy and accent tokens

The knowledge panel should feel like a natural continuation of the demo, not a separate admin page.

## Frontend Architecture

All changes stay inside the existing Thymeleaf template:

- add HTML markup for the new panel
- add CSS for file input, text input, upload result, and error/success states
- add a small JavaScript upload handler alongside the current page script

No new controller or page route is needed because the backend upload endpoint already exists.

## Error Handling

The frontend should block obviously invalid submissions before making the request:

- no file selected
- category is blank

Request failures should surface a short human-readable message. If the backend response body is not easily readable, fallback to a generic message like `Upload failed`.

## Testing

The existing template test is enough for this slice if extended to assert the new panel copy and DOM ids.

No browser automation is required for this iteration.

## Risks

- file input styling can be inconsistent across browsers, so the CSS should stay conservative
- adding more script state into `index.html` can become messy if the upload flow grows later

These are acceptable for the first version because the UI is intentionally minimal.

# Document Service Seeded Resources Design

**Context**

`DocumentService` currently hard-codes two seeded documents inside its constructor. This makes the service class noisy, couples document content to Java source, and makes future sample document edits harder to review.

**Decision**

Move the seeded markdown/text payloads into classpath resources under `src/main/resources/documents/` and let `DocumentService` read them during initialization.

**Approach**

`DocumentService` will keep ownership of the default seed metadata (`docId`, `title`, and resource path), but the actual document body will live in resource files. This keeps the current runtime behavior and document identities stable while removing the embedded large text block from the constructor.

For testability, `DocumentService` will support resource loading through a `ResourceLoader`-based constructor. Production wiring will use Spring's `ResourceLoader`, while tests can provide a deterministic loader or `DefaultResourceLoader`.

**Data Flow**

1. `DocumentService` starts.
2. It iterates over the built-in seed definitions.
3. For each seed definition, it loads the classpath resource content as UTF-8 text.
4. It creates the `Document` object and stores it in the in-memory map.

**Error Handling**

If a seeded resource cannot be loaded, initialization should fail fast with an `IllegalStateException`. Seed documents are required boot data, so a partial startup would be harder to diagnose than a clear failure.

**Testing**

- Add a focused test proving the service reads seeded content through the configured `ResourceLoader`.
- Keep the existing default-content test so the public behavior remains covered.
- Run the `DocumentServiceTest` suite after the change.

**Scope**

In scope:
- Extract the two seeded documents into resource files
- Update `DocumentService` to load seeded content from classpath resources
- Adjust tests for constructor changes and resource-backed loading

Out of scope:
- Externalizing document metadata into YAML/JSON
- Supporting dynamic scanning of arbitrary seeded documents
- Changing document IDs, titles, or downstream APIs

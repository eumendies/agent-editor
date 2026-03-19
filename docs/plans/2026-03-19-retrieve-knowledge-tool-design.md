# Retrieve Knowledge Tool Design

## Goal

Add a `retrieveKnowledge` agent.v2 tool that lets agents query the in-memory personal knowledge base through a shared retrieval service instead of accessing storage directly.

## Decisions

- Retrieval remains query-driven. `documentIds` is only an optional filter, not the retrieval key.
- The first implementation uses keyword scoring over stored chunks, but the service contract is shaped for later hybrid retrieval.
- The tool returns a JSON array of chunk results, not a final answer.
- Each returned item includes `documentId`, `fileName`, `chunkIndex`, `heading`, `chunkText`, and `score`.
- `query` is required. `topK` is optional and falls back to the configured default.

## Components

- `KnowledgeChunkRepository`: retrieval-facing abstraction over stored chunks
- `KnowledgeRetrievalService`: shared query service used by both RAG endpoints and the tool
- `RetrieveKnowledgeTool`: agent.v2 tool wrapper that decodes arguments and returns JSON results
- `ToolConfig`: registers the new tool in the runtime registry

## Non-Goals

- No final-answer generation inside the tool
- No vector retrieval in this increment
- No UI wiring in this increment

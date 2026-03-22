# Hybrid RAG Retrieval Design

## Goal

Upgrade the current RAG retrieval path from vector-only semantic search to native Milvus hybrid retrieval so uploaded knowledge chunks can be recalled by both dense embeddings and BM25 lexical matching over `heading + chunkText`. Keep the external retrieval API unchanged while making hybrid retrieval the default backend behavior.

## Decisions

- Use native Milvus hybrid retrieval instead of application-side score fusion.
- Create a new collection for hybrid retrieval rather than mutating the existing vector-only collection in place.
- Keep dense retrieval on the existing `embedding` field with `COSINE`.
- Add lexical retrieval over `heading + chunkText`, but not `fileName` or `category`.
- Introduce a dedicated `fullText` field for lexical search instead of overloading `chunkText`.
- Keep `KnowledgeRetrievalService.retrieve(query, documentIds, topK)` as the public entry point.
- Evolve the repository contract so hybrid retrieval is a first-class capability rather than hidden inside the service.
- Allow query-time fallback from hybrid search to vector search if the hybrid request fails.
- Treat schema or function initialization errors as startup failures rather than silently degrading to partial retrieval behavior.
- Require re-uploading knowledge documents after switching to the new collection.

## Recommended Approach

Create and switch to a dedicated Milvus collection such as `knowledge_chunks_v2`. This keeps the hybrid schema, BM25 function, and sparse index setup isolated from the existing vector-only collection and avoids carrying long-term compatibility code in the repository and config layers.

## Components

- `MilvusConfig`: creates the hybrid-ready collection schema, BM25 function, and indexes
- `MilvusKnowledgeChunkRepository`: persists `fullText`, issues Milvus hybrid search requests, maps results, and handles query-time fallback to vector search
- `KnowledgeChunkRepository`: exposes hybrid retrieval as the primary repository contract
- `KnowledgeBaseService`: prepares upload payloads, including `embedding` and `fullText`
- `KnowledgeRetrievalService`: validates inputs, resolves `topK`, builds the query vector, and delegates retrieval

## Collection Schema

The new collection keeps the existing business fields and adds a lexical-search-oriented field:

- `id`
- `documentId`
- `fileName`
- `chunkIndex`
- `heading`
- `category`
- `documentType`
- `chunkText`
- `embedding`
- `fullText`

`fullText` is built as `heading + "\n" + chunkText` when `heading` exists, otherwise just `chunkText`. This keeps presentation fields unchanged while giving Milvus one dedicated text field for BM25 analysis and matching.

## Index And Function Setup

- `embedding` keeps the current dense index setup: `AUTOINDEX + COSINE`
- `fullText` is configured with `enableAnalyzer=true` and `enableMatch=true`
- A BM25 function is added with `inputFieldNames=[fullText]`
- The BM25 function writes into a dedicated sparse retrieval field
- The sparse retrieval field uses `SPARSE_INVERTED_INDEX`

This makes Milvus responsible for both the dense and lexical retrieval branches and for final ranking within the hybrid search request.

## Retrieval Flow

1. `KnowledgeRetrievalService.retrieve(query, documentIds, topK)` validates the query and resolves the final result size.
2. The service embeds the query through `KnowledgeEmbeddingService`.
3. The repository issues a Milvus `hybridSearch` request with two sub-searches:
   - dense search on `embedding` using the query vector
   - lexical search using `EmbeddedText(query)` against the BM25-backed field derived from `fullText`
4. Both sub-searches receive the same `documentId` filter when `documentIds` is supplied.
5. Milvus returns a single ranked result list which is mapped to `RetrievedKnowledgeChunk`.

## Candidate Sizing

The final response size still follows the existing `topK` behavior. Each hybrid branch should use a larger candidate pool than the final response, for example `max(topK * 3, 20)`, so the fusion stage has a meaningful set of candidates to rank. This can start as a fixed default and be promoted to config only if tuning becomes necessary.

## Ranking Strategy

Use Milvus native hybrid ranking for the first implementation. Do not add Java-side custom score normalization or fusion logic in this increment. This keeps retrieval behavior concentrated in one system and avoids duplicating ranking semantics in the application layer.

## Repository Boundaries

`KnowledgeChunkRepository` should evolve from a vector-specific retrieval API toward a hybrid-first API, for example:

- `searchHybrid(String query, float[] queryVector, List<String> documentIds, int topK)`
- keep `searchByVector(...)` as a fallback-only path

This keeps hybrid retrieval explicit in the domain contract and prevents the service layer from becoming a place where retrieval strategies are manually stitched together.

## Upload And Persistence Changes

`KnowledgeBaseService` continues to parse and split uploaded documents as before, then:

- generates `embedding` per chunk
- generates `fullText` from `heading + chunkText`
- persists both fields through `MilvusKnowledgeChunkRepository`

The displayed chunk text remains `chunkText`; `fullText` exists only to support lexical search.

## Error Handling

Query-time behavior:

- blank query returns an empty list
- hybrid search failure logs a clear warning and falls back to vector search
- vector fallback failure propagates as a normal retrieval failure

Startup behavior:

- missing hybrid schema pieces, BM25 function, or required indexes should fail application startup
- the system should not silently run in a half-configured hybrid mode

Embedding errors should remain visible rather than silently degrading to lexical-only retrieval, because these failures usually indicate an upstream model or configuration problem that operators need to notice.

## Testing Strategy

Repository tests should verify:

- `saveAll` writes `fullText`
- hybrid search requests contain both dense and lexical branches
- both branches apply the same `documentId` filter
- result mapping still returns `RetrievedKnowledgeChunk`

Service tests should verify:

- default `topK` handling remains correct
- the service passes both `query` and `queryVector` to the repository
- hybrid failure triggers vector fallback
- blank queries still return an empty list

Configuration tests should verify:

- the collection schema contains `fullText`
- `fullText` enables analyzer and match flags
- BM25 function registration is present
- both dense and sparse indexes are created

## Migration And Rollout

- change the default collection name to a new collection such as `knowledge_chunks_v2`
- do not auto-migrate data from the old collection
- require users to re-upload knowledge documents so the hybrid-ready fields and indexes are rebuilt

This is a schema-breaking operational change, but it keeps the code path much simpler and reduces long-term maintenance cost.

## Non-Goals

- no automatic migration job from `knowledge_chunks` to `knowledge_chunks_v2`
- no lexical indexing over `fileName` or `category`
- no Java-side manual reranking or score fusion in this increment
- no long-term dual-mode compatibility layer between old and new Milvus collections

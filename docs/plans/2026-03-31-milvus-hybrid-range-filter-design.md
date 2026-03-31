# Milvus Hybrid Range Filter Design

**Context**

`MilvusKnowledgeChunkRepository` currently performs hybrid retrieval with two `AnnSearchReq` branches:

- dense vector search on `embedding` with `COSINE`
- sparse search on `sparseFullText` with `BM25`

The final returned score from hybrid retrieval is an RRF-style fusion score. Its absolute value is small and not suitable for prompting an LLM to treat low values as weak evidence. The user wants to reduce low-quality retrieval results earlier by applying Milvus-native range filtering on each search branch, with per-branch thresholds defined in configuration.

**Goals**

- Add configurable range-filter parameters for the dense and sparse hybrid search branches.
- Apply those parameters directly when constructing Milvus `AnnSearchReq`.
- Keep the change scoped to repository/configuration behavior.
- Do not mix this work with a separate “hide score from researcher” protocol change.

**Recommended Approach**

Add hybrid range-filter configuration under `milvus.*`, not `rag.*`.

Suggested structure:

```yaml
milvus:
  hybrid:
    dense:
      radius: ...
      range-filter: ...
    sparse:
      radius: ...
      range-filter: ...
```

These values should map directly to Milvus request parameters so there is no extra “minimum score” translation layer owned by our code.

**Why `milvus.*`**

These parameters are Milvus-specific request controls, not abstract retrieval-policy concepts. Keeping them in `MilvusProperties` preserves clean layering and makes it obvious which backend capability they tune.

**Code Changes**

1. `MilvusProperties`
   - add nested config objects for `hybrid.dense` and `hybrid.sparse`
   - each nested object exposes `radius` and `rangeFilter`
2. `application.yml`
   - add default values for both branches
3. `MilvusKnowledgeChunkRepository`
   - when building the dense `AnnSearchReq`, set `radius` and `rangeFilter` from `milvus.hybrid.dense`
   - when building the sparse `AnnSearchReq`, set `radius` and `rangeFilter` from `milvus.hybrid.sparse`

**Scope Boundary**

This change does **not** remove `score` from `RetrievedKnowledgeChunk` and does **not** alter the tool result schema shown to `ResearcherAgent`. That should be treated as a separate follow-up so the retrieval filtering change stays isolated and easy to verify.

**Testing Strategy**

Primary test coverage should be in `MilvusKnowledgeChunkRepositoryTest`:

- verify the dense search request carries configured `radius` / `rangeFilter`
- verify the sparse search request carries configured `radius` / `rangeFilter`
- preserve existing assertions around collection name, topK, and vector field names

If configuration binding needs protection, add a focused config-properties test; otherwise keep verification at the repository request-construction layer.

**Review Notes**

- 这里的配置语义应和 Milvus 原生参数一一对应，不要在代码里再额外引入“最小分数阈值”的抽象翻译层。
- 先把低相关结果过滤前移到检索层，再单独评估是否需要对 researcher 隐藏 `score` 字段，避免两个行为变化混在一个提交里难以回归。

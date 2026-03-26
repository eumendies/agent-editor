# Real Embedding Bad Case Design

**目标**

提供一个可手动触发的真实 embedding 测试，用来在测试代码里维护一组字符串候选和一个 query，观察余弦相似度排序结果，快速发现 RAG 召回中的 bad case。

**设计**

- 新增一个独立测试类 `src/test/java/com/agent/editor/service/KnowledgeEmbeddingBadCaseTest.java`
- 测试直接使用真实 `OpenAiEmbeddingModel` 配置，经由 `KnowledgeEmbeddingService` 生成向量
- 测试内部定义 `query` 与 `candidates`，避免引入额外生产代码
- 使用余弦相似度计算排序结果并输出，方便人工分析语义误召回或漏召回
- 默认通过 `-DrealEmbeddingTest=true` 开关控制，避免进入常规 `mvn test` 的联网路径

**错误处理**

- 未显式开启时直接跳过测试
- 发生远端调用失败时让测试报错，便于识别真实接口或配额问题

**测试策略**

- 仅验证结果非空、分数按降序排列，避免对模型语义结果做脆弱断言
- 主要输出排序明细，服务于 bad case 探查

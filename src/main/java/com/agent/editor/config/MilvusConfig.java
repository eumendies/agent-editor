package com.agent.editor.config;

import com.agent.editor.repository.KnowledgeChunkRepository;
import com.agent.editor.repository.MilvusKnowledgeChunkRepository;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MilvusConfig {

    private static final String COLLECTION_DESCRIPTION = "Knowledge chunks for personal RAG";
    private static final String ID_FIELD = "id";
    private static final String DOCUMENT_ID_FIELD = "documentId";
    private static final String FILE_NAME_FIELD = "fileName";
    private static final String CHUNK_INDEX_FIELD = "chunkIndex";
    private static final String HEADING_FIELD = "heading";
    private static final String CATEGORY_FIELD = "category";
    private static final String DOCUMENT_TYPE_FIELD = "documentType";
    private static final String CHUNK_TEXT_FIELD = "chunkText";
    private static final String FULL_TEXT_FIELD = "fullText";
    private static final String SPARSE_FULL_TEXT_FIELD = "sparseFullText";
    private static final String EMBEDDING_FIELD = "embedding";
    private static final String BM25_FUNCTION_NAME = "fulltext_bm25";

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MilvusClientV2 milvusClientV2(MilvusProperties properties) {
        return new MilvusClientV2(ConnectConfig.builder()
                .uri("http://" + properties.getHost() + ":" + properties.getPort())
                .build());
    }

    @Bean
    @Primary
    public KnowledgeChunkRepository knowledgeChunkRepository(MilvusClientV2 milvusClient,
                                                             MilvusProperties properties) {
        return new MilvusKnowledgeChunkRepository(milvusClient, properties);
    }

    @Bean
    public InitializingBean milvusCollectionInitializer(MilvusClientV2 milvusClient,
                                                        MilvusProperties properties) {
        return () -> {
            if (!milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(properties.getCollectionName())
                    .build())) {
                milvusClient.createCollection(CreateCollectionReq.builder()
                        .collectionName(properties.getCollectionName())
                        .description(COLLECTION_DESCRIPTION)
                        .collectionSchema(buildSchema(properties))
                        .build());
                milvusClient.createIndex(CreateIndexReq.builder()
                        .collectionName(properties.getCollectionName())
                        .indexParams(List.of(
                                IndexParam.builder()
                                        .fieldName(EMBEDDING_FIELD)
                                        .indexName("embedding_idx")
                                        .indexType(IndexParam.IndexType.AUTOINDEX)
                                        .metricType(IndexParam.MetricType.COSINE)
                                        .extraParams(Map.of())
                                        .build(),
                                IndexParam.builder()
                                        .fieldName(SPARSE_FULL_TEXT_FIELD)
                                        .indexName("sparse_full_text_idx")
                                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                        .metricType(IndexParam.MetricType.BM25)
                                        .extraParams(Map.of())
                                        .build()
                        ))
                        .build());
            }

            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(properties.getCollectionName())
                    .build());
        };
    }

    private CreateCollectionReq.CollectionSchema buildSchema(MilvusProperties properties) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .build();
        schema.addField(varcharField(ID_FIELD, 128, true));
        schema.addField(varcharField(DOCUMENT_ID_FIELD, 128, false));
        schema.addField(varcharField(FILE_NAME_FIELD, 256, false));
        schema.addField(AddFieldReq.builder()
                .fieldName(CHUNK_INDEX_FIELD)
                .dataType(DataType.Int64)
                .build());
        schema.addField(varcharField(HEADING_FIELD, 512, false));
        schema.addField(varcharField(CATEGORY_FIELD, 128, false));
        schema.addField(varcharField(DOCUMENT_TYPE_FIELD, 64, false));
        schema.addField(varcharField(CHUNK_TEXT_FIELD, 8192, false));
        schema.addField(AddFieldReq.builder()
                .fieldName(FULL_TEXT_FIELD)
                .dataType(DataType.VarChar)
                .maxLength(12288)
                .enableAnalyzer(true)
                .enableMatch(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(SPARSE_FULL_TEXT_FIELD)
                .dataType(DataType.SparseFloatVector)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(EMBEDDING_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(properties.getEmbeddingDimension())
                .build());
        schema.addFunction(CreateCollectionReq.Function.builder()
                .name(BM25_FUNCTION_NAME)
                .functionType(FunctionType.BM25)
                .inputFieldNames(List.of(FULL_TEXT_FIELD))
                .outputFieldNames(List.of(SPARSE_FULL_TEXT_FIELD))
                .build());
        return schema;
    }

    private AddFieldReq varcharField(String fieldName, int maxLength, boolean primaryKey) {
        return AddFieldReq.builder()
                .fieldName(fieldName)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .isPrimaryKey(primaryKey)
                .autoID(false)
                .build();
    }
}

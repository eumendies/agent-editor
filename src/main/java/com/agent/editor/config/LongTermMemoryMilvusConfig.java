package com.agent.editor.config;

import com.agent.editor.repository.LongTermMemoryRepository;
import com.agent.editor.repository.MilvusLongTermMemoryRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LongTermMemoryMilvusConfig {

    private static final String MEMORY_ID = "memoryId";
    private static final String MEMORY_TYPE = "memoryType";
    private static final String DOCUMENT_ID = "documentId";
    private static final String SUMMARY = "summary";
    private static final String DETAILS = "details";
    private static final String SOURCE_TASK_ID = "sourceTaskId";
    private static final String SOURCE_SESSION_ID = "sourceSessionId";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";
    private static final String EMBEDDING = "embedding";

    @Bean
    public LongTermMemoryRepository longTermMemoryRepository(MilvusClientV2 milvusClient,
                                                             LongTermMemoryMilvusProperties properties) {
        return new MilvusLongTermMemoryRepository(milvusClient, properties);
    }

    @Bean
    public InitializingBean longTermMemoryCollectionInitializer(MilvusClientV2 milvusClient,
                                                                LongTermMemoryMilvusProperties properties) {
        return () -> {
            if (!milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(properties.getCollectionName())
                    .build())) {
                milvusClient.createCollection(CreateCollectionReq.builder()
                        .collectionName(properties.getCollectionName())
                        .description("Confirmed agent long-term memories")
                        .collectionSchema(buildSchema(properties))
                        .build());
                milvusClient.createIndex(CreateIndexReq.builder()
                        .collectionName(properties.getCollectionName())
                        .indexParams(List.of(IndexParam.builder()
                                .fieldName(EMBEDDING)
                                .indexName("embedding_idx")
                                .indexType(IndexParam.IndexType.AUTOINDEX)
                                .metricType(IndexParam.MetricType.COSINE)
                                .extraParams(Map.of())
                                .build()))
                        .build());
            }

            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(properties.getCollectionName())
                    .build());
        };
    }

    private CreateCollectionReq.CollectionSchema buildSchema(LongTermMemoryMilvusProperties properties) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .build();
        schema.addField(varcharField(MEMORY_ID, 128, true));
        schema.addField(varcharField(MEMORY_TYPE, 64, false));
        schema.addField(nullableVarcharField(DOCUMENT_ID, 128));
        schema.addField(varcharField(SUMMARY, 4096, false));
        schema.addField(varcharField(DETAILS, 8192, false));
        schema.addField(nullableVarcharField(SOURCE_TASK_ID, 128));
        schema.addField(nullableVarcharField(SOURCE_SESSION_ID, 128));
        schema.addField(varcharField(CREATED_AT, 64, false));
        schema.addField(varcharField(UPDATED_AT, 64, false));
        schema.addField(AddFieldReq.builder()
                .fieldName(EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(properties.getEmbeddingDimension())
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

    private AddFieldReq nullableVarcharField(String fieldName, int maxLength) {
        return AddFieldReq.builder()
                .fieldName(fieldName)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .isNullable(true)
                .build();
    }
}

package com.agent.editor.config;

import com.agent.editor.repository.KnowledgeChunkRepository;
import com.agent.editor.repository.MilvusKnowledgeChunkRepository;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusConfigTest {

    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "milvus.host=localhost",
                    "milvus.port=19530",
                    "milvus.collection-name=knowledge_chunks_v2",
                    "milvus.embedding-dimension=1024"
            )
            .withUserConfiguration(MilvusConfig.class, TestConfig.class);

    private final ApplicationContextRunner enabledContextRunner = baseContextRunner
            .withBean(MilvusClientV2.class, () -> {
                MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
                when(milvusClient.hasCollection(any())).thenReturn(true);
                return milvusClient;
            });

    @Test
    void shouldRegisterMilvusRepositoryAsPrimaryKnowledgeChunkRepository() {
        enabledContextRunner.run(context -> {
            assertThat(context).hasSingleBean(KnowledgeChunkRepository.class);
            assertThat(context.getBean(KnowledgeChunkRepository.class))
                    .isInstanceOf(MilvusKnowledgeChunkRepository.class);
        });
    }

    @Test
    void shouldNotCreateMilvusBeansWhenDisabled() {
        baseContextRunner.withPropertyValues("milvus.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(MilvusClientV2.class);
            assertThat(context).doesNotHaveBean(MilvusKnowledgeChunkRepository.class);
        });
    }

    @Test
    void shouldCreateAndLoadCollectionWhenMissing() throws Exception {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.hasCollection(any())).thenReturn(false);
        MilvusConfig milvusConfig = new MilvusConfig();

        InitializingBean initializer = milvusConfig.milvusCollectionInitializer(
                milvusClient,
                new MilvusProperties("localhost", 19530, "knowledge_chunks_v2", 1024)
        );

        initializer.afterPropertiesSet();

        ArgumentCaptor<CreateCollectionReq> collectionCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        ArgumentCaptor<CreateIndexReq> indexCaptor = ArgumentCaptor.forClass(CreateIndexReq.class);

        verify(milvusClient).createCollection(collectionCaptor.capture());
        verify(milvusClient).createIndex(indexCaptor.capture());
        verify(milvusClient).loadCollection(any(LoadCollectionReq.class));

        CreateCollectionReq collectionRequest = collectionCaptor.getValue();
        assertThat(collectionRequest.getCollectionName()).isEqualTo("knowledge_chunks_v2");
        assertThat(collectionRequest.getCollectionSchema().getFieldSchemaList())
                .extracting(
                        CreateCollectionReq.FieldSchema::getName,
                        CreateCollectionReq.FieldSchema::getDataType,
                        CreateCollectionReq.FieldSchema::getEnableAnalyzer,
                        CreateCollectionReq.FieldSchema::getEnableMatch
                )
                .contains(
                        tuple("embedding", DataType.FloatVector, null, null),
                        tuple("fullText", DataType.VarChar, true, true),
                        tuple("sparseFullText", DataType.SparseFloatVector, null, null)
                );
        assertThat(collectionRequest.getCollectionSchema().getFunctionList())
                .extracting(
                        CreateCollectionReq.Function::getName,
                        CreateCollectionReq.Function::getFunctionType,
                        CreateCollectionReq.Function::getInputFieldNames,
                        CreateCollectionReq.Function::getOutputFieldNames
                )
                .contains(tuple(
                        "fulltext_bm25",
                        FunctionType.BM25,
                        java.util.List.of("fullText"),
                        java.util.List.of("sparseFullText")
                ));

        CreateIndexReq indexRequest = indexCaptor.getValue();
        assertThat(indexRequest.getCollectionName()).isEqualTo("knowledge_chunks_v2");
        assertThat(indexRequest.getIndexParams())
                .extracting(IndexParam::getFieldName, IndexParam::getIndexType, IndexParam::getMetricType)
                .contains(
                        tuple("embedding", IndexParam.IndexType.AUTOINDEX, IndexParam.MetricType.COSINE),
                        tuple("sparseFullText", IndexParam.IndexType.SPARSE_INVERTED_INDEX, IndexParam.MetricType.BM25)
                );
    }

    @Configuration
    @EnableConfigurationProperties(MilvusProperties.class)
    static class TestConfig {
    }
}

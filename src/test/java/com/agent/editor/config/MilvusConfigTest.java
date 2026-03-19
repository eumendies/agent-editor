package com.agent.editor.config;

import com.agent.editor.repository.KnowledgeChunkRepository;
import com.agent.editor.repository.MilvusKnowledgeChunkRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusConfigTest {

    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "milvus.host=localhost",
                    "milvus.port=19530",
                    "milvus.collection-name=knowledge_chunks",
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
                new MilvusProperties("localhost", 19530, "knowledge_chunks", 1024)
        );

        initializer.afterPropertiesSet();

        verify(milvusClient).createCollection(any(CreateCollectionReq.class));
        verify(milvusClient).createIndex(any(CreateIndexReq.class));
        verify(milvusClient).loadCollection(any(LoadCollectionReq.class));
    }

    @Configuration
    @EnableConfigurationProperties(MilvusProperties.class)
    static class TestConfig {
    }
}

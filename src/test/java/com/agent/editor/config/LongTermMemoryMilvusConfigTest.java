package com.agent.editor.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.InitializingBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryMilvusConfigTest {

    @Test
    void shouldAllowNullDocumentIdInLongTermMemorySchema() throws Exception {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.hasCollection(any())).thenReturn(false);
        LongTermMemoryMilvusConfig config = new LongTermMemoryMilvusConfig();

        InitializingBean initializer = config.longTermMemoryCollectionInitializer(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 1024)
        );

        initializer.afterPropertiesSet();

        ArgumentCaptor<CreateCollectionReq> requestCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(milvusClient).createCollection(requestCaptor.capture());
        CreateCollectionReq.FieldSchema documentIdField = requestCaptor.getValue()
                .getCollectionSchema()
                .getField("documentId");

        assertEquals("documentId", documentIdField.getName());
        assertTrue(Boolean.TRUE.equals(documentIdField.getIsNullable()));
    }

    @Test
    void shouldAllowNullSourceFieldsInLongTermMemorySchema() throws Exception {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        when(milvusClient.hasCollection(any())).thenReturn(false);
        LongTermMemoryMilvusConfig config = new LongTermMemoryMilvusConfig();

        InitializingBean initializer = config.longTermMemoryCollectionInitializer(
                milvusClient,
                new LongTermMemoryMilvusProperties("long_term_memory_v1", 1024)
        );

        initializer.afterPropertiesSet();

        ArgumentCaptor<CreateCollectionReq> requestCaptor = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(milvusClient).createCollection(requestCaptor.capture());
        CreateCollectionReq.CollectionSchema schema = requestCaptor.getValue().getCollectionSchema();

        assertTrue(Boolean.TRUE.equals(schema.getField("sourceTaskId").getIsNullable()));
        assertTrue(Boolean.TRUE.equals(schema.getField("sourceSessionId").getIsNullable()));
    }
}

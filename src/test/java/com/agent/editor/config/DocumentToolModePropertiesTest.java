package com.agent.editor.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentToolModePropertiesTest {

    @Test
    void shouldKeepConfiguredThresholdValue() {
        DocumentToolModeProperties properties = com.agent.editor.testsupport.ConfigurationTestFixtures.documentToolModeProperties(4321);

        assertEquals(4321, properties.getLongDocumentThresholdTokens());
    }
}

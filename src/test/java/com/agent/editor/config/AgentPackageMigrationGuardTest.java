package com.agent.editor.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentPackageMigrationGuardTest {

    @Test
    void shouldNotReferenceVersionedAgentPackages() throws IOException {
        String mainSources = readTextFiles(Path.of("src/main/java"), ".java");
        String testSources = readTextFiles(Path.of("src/test/java"), ".java");

        assertFalse(mainSources.contains("com.agent.editor.agent.v2"));
        assertFalse(mainSources.contains("com.agent.editor.agent.v1"));
        assertFalse(testSources.contains("com.agent.editor.agent.v2"));
        assertFalse(testSources.contains("com.agent.editor.agent.v1"));
    }

    @Test
    void shouldNotReferenceLegacyAgentVersionMarkersOrRoutes() throws IOException {
        String mainSources = readTextFiles(Path.of("src/main/java"), ".java");
        String testSources = readTextFiles(Path.of("src/test/java"), ".java");
        String templates = readTextFiles(Path.of("src/main/resources/templates"), ".html");

        assertFalse(mainSources.contains("AgentV2"));
        assertFalse(testSources.contains("AgentV2"));
        assertFalse(mainSources.contains("/api/v2/agent"));
        assertFalse(testSources.contains("/api/v2/agent"));
        assertFalse(templates.contains("/api/v2/agent"));
        assertFalse(mainSources.contains("/api/v2/memory"));
        assertFalse(testSources.contains("/api/v2/memory"));
        assertFalse(templates.contains("/api/v2/memory"));
        assertFalse(mainSources.contains("/ws/agent/v2"));
        assertFalse(testSources.contains("/ws/agent/v2"));
        assertFalse(templates.contains("/ws/agent/v2"));
    }

    private String readTextFiles(Path root, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(suffix))
                    .filter(path -> !path.endsWith("AgentPackageMigrationGuardTest.java"))
                    .map(this::readSafely)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private String readSafely(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}

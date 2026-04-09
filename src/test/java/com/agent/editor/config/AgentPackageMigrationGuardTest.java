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
        String mainSources = readJavaSources(Path.of("src/main/java/com/agent/editor/agent"));
        String testSources = readJavaSources(Path.of("src/test/java/com/agent/editor/agent"));

        assertFalse(mainSources.contains("com.agent.editor.agent.v2"));
        assertFalse(mainSources.contains("com.agent.editor.agent.v1"));
        assertFalse(testSources.contains("com.agent.editor.agent.v2"));
        assertFalse(testSources.contains("com.agent.editor.agent.v1"));
    }

    private String readJavaSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
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

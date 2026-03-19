package com.agent.editor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiEditorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEditorApplication.class, args);
    }
}

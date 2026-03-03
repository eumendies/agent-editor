package com.agent.editor.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.Arrays;
import java.util.List;

final class AgentTools {
    
    private AgentTools() {}
    
    static final String READ_DOCUMENT = "readDocument";
    static final String EDIT_DOCUMENT = "editDocument";
    static final String SEARCH_CONTENT = "searchContent";
    static final String FORMAT_DOCUMENT = "formatDocument";
    static final String ANALYZE_DOCUMENT = "analyzeDocument";
    static final String UNDO_CHANGE = "undoChange";
    static final String PREVIEW_CHANGES = "previewChanges";
    static final String COMPARE_VERSIONS = "compareVersions";
    static final String TERMINATE_TASK = "terminateTask";
    static final String RESPOND_TO_USER = "respondToUser";
    
    static List<ToolSpecification> defaultTools() {
        return Arrays.asList(
            ToolSpecification.builder()
                .name(READ_DOCUMENT)
                .description("Read the current document content")
                .build(),
            
            ToolSpecification.builder()
                .name(EDIT_DOCUMENT)
                .description("Edit the document content with specified changes")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("content", "The new content to replace the document")
                    .required("content")
                    .build())
                .build(),
            
            ToolSpecification.builder()
                .name(SEARCH_CONTENT)
                .description("Search for specific text in the document")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("pattern", "The text pattern to search for")
                    .required("pattern")
                    .build())
                .build(),
            
            ToolSpecification.builder()
                .name(FORMAT_DOCUMENT)
                .description("Format the document with indentation")
                .build(),
            
            ToolSpecification.builder()
                .name(ANALYZE_DOCUMENT)
                .description("Analyze the document for word count, line count, etc.")
                .build(),
            
            ToolSpecification.builder()
                .name(UNDO_CHANGE)
                .description("Undo the last change")
                .build(),
            
            ToolSpecification.builder()
                .name(PREVIEW_CHANGES)
                .description("Preview changes before applying")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("content", "Content to preview")
                    .required("content")
                    .build())
                .build(),
            
            ToolSpecification.builder()
                .name(COMPARE_VERSIONS)
                .description("Compare current document with original")
                .build(),
            
            ToolSpecification.builder()
                .name(TERMINATE_TASK)
                .description("Terminate the current task immediately")
                .build(),
            
            ToolSpecification.builder()
                .name(RESPOND_TO_USER)
                .description("Send a message directly to the user")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("message", "The message to send to the user")
                    .required("message")
                    .build())
                .build()
        );
    }
}

package com.agent.editor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private String id;
    private String fileName;
    private String category;
    private String status;
    private Instant createdAt;
}

package com.agent.editor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedKnowledgeDocument {

    private String content;
    private String documentType;
}

package com.agent.editor.agent.v2.tool.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveKnowledgeArguments {

    private String query;
    private List<String> documentIds;
    private Integer topK;
}

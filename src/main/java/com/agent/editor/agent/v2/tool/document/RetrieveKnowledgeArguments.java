package com.agent.editor.agent.v2.tool.document;

import java.util.List;

public record RetrieveKnowledgeArguments(
        String query,
        List<String> documentIds,
        Integer topK
) {
}

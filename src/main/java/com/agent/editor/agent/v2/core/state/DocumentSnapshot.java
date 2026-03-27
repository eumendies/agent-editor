package com.agent.editor.agent.v2.core.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSnapshot {

    private String documentId;
    private String title;
    private String content;
}

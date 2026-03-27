package com.agent.editor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagCitation {

    private int index;
    private String fileName;
    private int chunkIndex;
    private String excerpt;
}

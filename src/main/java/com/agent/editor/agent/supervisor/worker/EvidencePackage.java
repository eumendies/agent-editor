package com.agent.editor.agent.supervisor.worker;

import com.agent.editor.model.EvidenceChunk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * researcher 输出的标准证据载体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidencePackage {

    // researcher 最终返回的证据包，既给 writer 使用，也给 reviewer 判断“是否有据可依”。
    private List<String> queries;
    private String evidenceSummary;
    private String limitations;
    private List<String> uncoveredPoints;
    private List<EvidenceChunk> chunks;
}

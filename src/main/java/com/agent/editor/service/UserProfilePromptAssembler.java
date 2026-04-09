package com.agent.editor.service;

import com.agent.editor.agent.core.memory.LongTermMemoryItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfilePromptAssembler {

    private static final int MAX_PROFILE_LINES = 5;

    public String assemble(List<LongTermMemoryItem> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return "";
        }
        List<String> lines = profiles.stream()
                .map(LongTermMemoryItem::getSummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .limit(MAX_PROFILE_LINES)
                .map(summary -> "- " + summary)
                .toList();
        if (lines.isEmpty()) {
            return "";
        }
        return "Confirmed user profile:\n" + String.join("\n", lines);
    }
}

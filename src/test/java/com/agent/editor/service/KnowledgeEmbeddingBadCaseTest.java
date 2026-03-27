package com.agent.editor.service;

import com.agent.editor.AiEditorApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AiEditorApplication.class,
        properties = {
                "langchain4j.open-ai.embedding-model.api-key=${EMBEDDING_API_KEY:}",
                "langchain4j.open-ai.embedding-model.model-name=text-embedding-v4",
                "langchain4j.open-ai.embedding-model.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnabledIfSystemProperty(named = "realEmbeddingTest", matches = "true")
class KnowledgeEmbeddingBadCaseTest {

    @Autowired
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Autowired
    private Environment environment;

    @Test
    void shouldRankCandidatesByCosineSimilarityWithRealEmbedding() {
        String query = "小米13";
        List<String> candidates = List.of(
                "小米12",
                "小米13",
                "小米14"
        );

        System.out.println("embedding base-url: "
                + environment.getProperty("langchain4j.open-ai.embedding-model.base-url"));
        float[] queryVector = knowledgeEmbeddingService.embed(query);
        List<ScoredCandidate> rankedCandidates = candidates.stream()
                .map(candidate -> new ScoredCandidate(
                        candidate,
                        cosineSimilarity(queryVector, knowledgeEmbeddingService.embed(candidate))
                ))
                .sorted(Comparator.comparingDouble(ScoredCandidate::getScore).reversed())
                .toList();

        assertEquals(candidates.size(), rankedCandidates.size());
        for (int i = 1; i < rankedCandidates.size(); i++) {
            assertTrue(rankedCandidates.get(i - 1).getScore() >= rankedCandidates.get(i).getScore());
        }

        System.out.println("query: " + query);
        rankedCandidates.forEach(candidate -> System.out.printf("%.6f | %s%n", candidate.getScore(), candidate.getText()));
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dotProduct += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    // 手工 bad case 分析更关注排序输出，保留最小结构即可。
    @Getter
    @AllArgsConstructor
    private static class ScoredCandidate {

        private final String text;
        private final double score;
    }
}

package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMergerTest {

    private SourceMerger merger;

    @BeforeEach
    void setUp() {
        merger = new SourceMerger(0.6, 0.4);
    }

    private static Document doc(String id, String content, String source) {
        return new Document(id, content, source);
    }

    private static HybridRagEngine.ScoredDocument scored(Document doc, double score, String source) {
        return new HybridRagEngine.ScoredDocument(doc, score, source);
    }

    @Nested
    @DisplayName("Merging and ranking")
    class MergingAndRanking {

        @Test
        void shouldReturnEmptyForNoInput() {
            var result = merger.mergeAndRerank("query", List.of(), 2);
            assertThat(result).isEmpty();
        }

        @Test
        void shouldDeduplicateById() {
            var d1 = doc("d1", "content", "confluence");
            var scored1 = scored(d1, 0.9, "confluence");
            var scored2 = scored(d1, 0.8, "redis-vector");

            var result = merger.mergeAndRerank("query", List.of(scored1, scored2), 2);
            assertThat(result).hasSize(1);
        }

        @Test
        void shouldApplySourceWeights() {
            // confluence weight = 0.6, redis weight = 0.4
            var d1 = doc("d1", "content", "confluence"); // score 0.8 * 0.6 = 0.48
            var d2 = doc("d2", "content", "redis-vector"); // score 0.9 * 0.4 = 0.36

            var result = merger.mergeAndRerank("query",
                List.of(scored(d1, 0.8, "confluence"), scored(d2, 0.9, "redis-vector")), 5);

            assertThat(result).hasSize(2);
            // d1 should rank higher despite lower raw score
            assertThat(result.get(0).getId()).isEqualTo("d1");
        }

        @Test
        void shouldUseDefaultWeightForUnknownSource() {
            var d1 = doc("d1", "content", "unknown-source"); // default weight 0.5
            var d2 = doc("d2", "content", "confluence"); // weight 0.6

            var result = merger.mergeAndRerank("query",
                List.of(scored(d1, 0.9, "unknown-source"), scored(d2, 0.9, "confluence")), 5);

            // d2 should rank higher (0.9 * 0.6 > 0.9 * 0.5)
            assertThat(result.get(0).getId()).isEqualTo("d2");
        }

        @Test
        void shouldSortByFinalScoreDescending() {
            var d1 = doc("d1", "c", "confluence");
            var d2 = doc("d2", "c", "confluence");
            var d3 = doc("d3", "c", "confluence");

            var result = merger.mergeAndRerank("query",
                List.of(scored(d1, 0.5, "confluence"), scored(d2, 0.9, "confluence"),
                      scored(d3, 0.7, "confluence")), 5);

            assertThat(result).extracting(Document::getId).containsExactly("d2", "d3", "d1");
        }
    }
}

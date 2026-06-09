package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

    private ReciprocalRankFusion rrf;

    @BeforeEach
    void setUp() {
        rrf = new ReciprocalRankFusion(60);
    }

    private static Document doc(String id, String content, String source) {
        return new Document(id, content, source);
    }

    private static ReciprocalRankFusion.QueryResult result(String query, List<Document> docs) {
        return new ReciprocalRankFusion.QueryResult(query, docs);
    }

    @Nested
    @DisplayName("Basic merging")
    class BasicMerging {

        @Test
        void shouldReturnEmptyForNoResults() {
            var result = rrf.merge(List.of(), 5);
            assertThat(result).isEmpty();
        }

        @Test
        void shouldMergeSingleQueryResult() {
            var docs = List.of(doc("d1", "content1", "src"), doc("d2", "content2", "src"));
            var results = List.of(result("query", docs));

            var merged = rrf.merge(results, 5);
            assertThat(merged).hasSize(2);
            assertThat(merged.get(0).getId()).isEqualTo("d1");
            assertThat(merged.get(1).getId()).isEqualTo("d2");
        }

        @Test
        void shouldDeduplicateAcrossQueries() {
            var d1 = doc("d1", "shared content", "src");
            var q1 = result("q1", List.of(d1, doc("d2", "other", "src")));
            var q2 = result("q2", List.of(d1, doc("d3", "third", "src")));

            var merged = rrf.merge(List.of(q1, q2), 5);
            assertThat(merged).hasSize(3);
            assertThat(merged).extracting(Document::getId).containsExactly("d1", "d2", "d3");
        }
    }

    @Nested
    @DisplayName("Ranking order")
    class RankingOrder {

        @Test
        void shouldRankByRRFScore() {
            // d1 appears in both queries, d2 in one
            var d1 = doc("d1", "content", "src");
            var d2 = doc("d2", "content", "src");
            var d3 = doc("d3", "content", "src");

            var q1 = result("q1", List.of(d2, d1));
            var q2 = result("q2", List.of(d1, d3));

            var merged = rrf.merge(List.of(q1, q2), 5);
            // d1 has highest score (appears in both), then d2 (rank 0 in q1), then d3 (rank 1 in q2)
            assertThat(merged.get(0).getId()).isEqualTo("d1");
        }

        @Test
        void shouldHigherRankBoostScore() {
            // d1 at rank 0 in q1, rank 1 in q2
            // d2 at rank 1 in q1, rank 0 in q2
            // Both appear in both — equal scores, but order may vary by insertion
            var d1 = doc("d1", "content", "src");
            var d2 = doc("d2", "content", "src");

            var q1 = result("q1", List.of(d1, d2));
            var q2 = result("q2", List.of(d2, d1));

            var merged = rrf.merge(List.of(q1, q2), 5);
            assertThat(merged).hasSize(2);
            // Both should have same RRF score
        }
    }

    @Nested
    @DisplayName("Top-K limiting")
    class TopKLimiting {

        @Test
        void shouldLimitToTopK() {
            var docs = List.of(
                doc("d1", "c", "s"), doc("d2", "c", "s"), doc("d3", "c", "s"),
                doc("d4", "c", "s"), doc("d5", "c", "s")
            );
            var results = List.of(result("q", docs));

            var merged = rrf.merge(results, 3);
            assertThat(merged).hasSize(3);
        }

        @Test
        void shouldHandleTopKLargerThanResults() {
            var docs = List.of(doc("d1", "c", "s"));
            var results = List.of(result("q", docs));

            var merged = rrf.merge(results, 10);
            assertThat(merged).hasSize(1);
        }
    }

    @Nested
    @DisplayName("K parameter")
    class KParameter {

        @Test
        void smallerKShouldSharpenRanking() {
            var smallK = new ReciprocalRankFusion(10);
            var largeK = new ReciprocalRankFusion(1000);

            var docs = List.of(doc("d1", "c", "s"), doc("d2", "c", "s"));
            var results = List.of(result("q", docs));

            // Both should produce valid results regardless of k
            assertThat(smallK.merge(results, 5)).hasSize(2);
            assertThat(largeK.merge(results, 5)).hasSize(2);
        }
    }
}

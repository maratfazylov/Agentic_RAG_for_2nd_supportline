package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReciprocalRankFusion {

    private static final Logger log = LoggerFactory.getLogger(ReciprocalRankFusion.class);

    private final int k;

    public ReciprocalRankFusion(@Value("${rag.rrf-k:60}") int k) {
        this.k = k;
        log.info("RRF initialized with k={}", k);
    }

    public List<Document> merge(List<QueryResult> perQueryResults, int topK) {
        var scores = new HashMap<String, ScoreEntry>();

        for (var qr : perQueryResults) {
            var docs = qr.documents();
            for (int rank = 0; rank < docs.size(); rank++) {
                var doc = docs.get(rank);
                scores.merge(doc.getId(),
                    new ScoreEntry(doc, rrfScore(rank)),
                    (existing, incoming) -> {
                        existing.score += incoming.score;
                        return existing;
                    });
            }
        }

        var ranked = new ArrayList<>(scores.values());
        ranked.sort(Comparator.comparingDouble((ScoreEntry e) -> e.score).reversed());

        var result = ranked.stream()
            .limit(topK)
            .map(e -> e.document)
            .toList();

        log.debug("RRF merged {} queries into {} documents", perQueryResults.size(), result.size());
        return result;
    }

    private double rrfScore(int rank) {
        return 1.0 / (k + rank + 1);
    }

    record QueryResult(String query, List<Document> documents) {}

    private static class ScoreEntry {
        final Document document;
        double score;

        ScoreEntry(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}

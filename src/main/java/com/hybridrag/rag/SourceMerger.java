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
public class SourceMerger {

    private static final Logger log = LoggerFactory.getLogger(SourceMerger.class);

    private final Map<String, Double> sourceWeights;

    public SourceMerger(
        @Value("${rag.source-weight.confluence:0.6}") double confluenceWeight,
        @Value("${rag.source-weight.redis-vector:0.4}") double redisWeight
    ) {
        this.sourceWeights = new HashMap<>();
        this.sourceWeights.put("confluence", confluenceWeight);
        this.sourceWeights.put("redis-vector", redisWeight);
        log.info("Source weights: {}", sourceWeights);
    }

    public List<Document> mergeAndRerank(String query, List<HybridRagEngine.ScoredDocument> scored, int sourceCount) {
        var merged = new ArrayList<Document>();
        var seen = new java.util.HashSet<String>();

        var sorted = scored.stream()
            .sorted(Comparator.comparingDouble(
                (HybridRagEngine.ScoredDocument sd) -> computeFinalScore(sd)
            ).reversed())
            .toList();

        for (var sd : sorted) {
            var doc = sd.document();
            if (seen.add(doc.getId())) {
                merged.add(doc);
            }
        }

        log.debug("Merger: {} input → {} unique documents", scored.size(), merged.size());
        return merged;
    }

    private double computeFinalScore(HybridRagEngine.ScoredDocument sd) {
        var sourceWeight = sourceWeights.getOrDefault(sd.source(), 0.5);
        return sd.score() * sourceWeight;
    }
}

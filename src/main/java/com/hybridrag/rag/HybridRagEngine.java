package com.hybridrag.rag;

import com.hybridrag.knowledge.KnowledgeSource;
import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class HybridRagEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridRagEngine.class);
    private static final int TOP_K_PER_SOURCE = 5;

    private final List<KnowledgeSource> sources;
    private final RagEngine ragEngine;
    private final SourceMerger sourceMerger;

    public HybridRagEngine(List<KnowledgeSource> sources, RagEngine ragEngine, SourceMerger sourceMerger) {
        this.sources = sources;
        this.ragEngine = ragEngine;
        this.sourceMerger = sourceMerger;
        log.info("HybridRagEngine initialized with sources: {}",
            sources.stream().map(KnowledgeSource::getName).toList());
    }

    public RetrievalResult retrieve(String query) {
        log.info("Hybrid retrieval for: {}", query);

        var futures = sources.stream()
            .map(s -> CompletableFuture.supplyAsync(() -> {
                var results = s.search(query, TOP_K_PER_SOURCE);
                log.debug("Source '{}' returned {} results", s.getName(), results.size());
                return new SourceResult(s.getName(), results);
            }))
            .toList();

        var allResults = futures.stream()
            .map(f -> f.orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("Source timed out or failed: {}", ex.getMessage());
                    return null;
                })
                .join())
            .filter(Objects::nonNull)
            .toList();

        return merge(allResults, query);
    }

    public String buildHybridPrompt(String query, RetrievalResult retrieval) {
        var docs = retrieval.mergedDocuments();
        var sb = new StringBuilder();

        sb.append("You are a Hybrid RAG AI Agent with access to multiple knowledge sources.\n\n");

        if (!docs.isEmpty()) {
            sb.append("--- Retrieved Context ---\n");
            for (int i = 0; i < docs.size(); i++) {
                var doc = docs.get(i);
                sb.append("[").append(i + 1).append("] (source: ")
                    .append(doc.getSource()).append(")\n")
                    .append(doc.getContent()).append("\n\n");
            }
            sb.append("--- End of Context ---\n\n");
        }

        sb.append("User question: ").append(query);
        return sb.toString();
    }

    private RetrievalResult merge(List<SourceResult> results, String query) {
        var merged = sourceMerger.mergeAndRerank(
            query,
            results.stream()
                .flatMap(r -> r.documents().stream()
                    .map(d -> new ScoredDocument(d, 1.0, r.sourceName())))
                .toList(),
            sources.size()
        );

        var perSource = new java.util.HashMap<String, List<Document>>();
        for (var r : results) {
            perSource.put(r.sourceName(), r.documents());
        }

        return new RetrievalResult(merged, perSource);
    }

    record SourceResult(String sourceName, List<Document> documents) {}

    public record ScoredDocument(Document document, double score, String source) {}

    public record RetrievalResult(
        List<Document> mergedDocuments,
        java.util.Map<String, List<Document>> perSource
    ) {}
}

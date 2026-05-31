package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class RagFusion {

    private static final Logger log = LoggerFactory.getLogger(RagFusion.class);

    private final QueryParaphraser paraphraser;
    private final HybridRagEngine hybridRag;
    private final ReciprocalRankFusion rrf;

    public RagFusion(QueryParaphraser paraphraser, HybridRagEngine hybridRag, ReciprocalRankFusion rrf) {
        this.paraphraser = paraphraser;
        this.hybridRag = hybridRag;
        this.rrf = rrf;
    }

    public FusionResult search(String query, int topK) {
        log.info("RAG Fusion for: {}", query);

        var paraphrased = paraphraser.paraphrase(query, 3);
        var allQueries = new ArrayList<>(paraphrased);
        allQueries.addFirst(query);

        log.info("Fusion queries ({}): {}", allQueries.size(), allQueries);

        var futures = allQueries.stream()
            .map(q -> CompletableFuture.supplyAsync(() -> {
                var retrieval = hybridRag.retrieve(q);
                log.debug("Fusion sub-query '{}' → {} docs", q, retrieval.mergedDocuments().size());
                return new ReciprocalRankFusion.QueryResult(q, retrieval.mergedDocuments());
            }))
            .toList();

        var perQueryResults = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        var fused = rrf.merge(perQueryResults, topK);

        log.info("Fusion result: {} queries → {} fused documents (topK={})",
            allQueries.size(), fused.size(), topK);

        return new FusionResult(fused, perQueryResults);
    }

    public String buildFusionPrompt(String query, FusionResult fusion) {
        var docs = fusion.mergedDocuments();
        var sb = new StringBuilder();

        sb.append("You are a Hybrid RAG AI Agent. Below is fused context ")
            .append("retrieved from multiple query variations.\n\n");

        if (!docs.isEmpty()) {
            sb.append("--- Fused Context ---\n");
            for (int i = 0; i < docs.size(); i++) {
                var doc = docs.get(i);
                sb.append("[").append(i + 1).append("] (source: ")
                    .append(doc.getSource()).append(")\n")
                    .append(doc.getContent()).append("\n\n");
            }
            sb.append("--- End of Context ---\n\n");
        }

        sb.append("Original question: ").append(query);
        return sb.toString();
    }

    public record FusionResult(
        List<Document> mergedDocuments,
        List<ReciprocalRankFusion.QueryResult> perQueryResults
    ) {}
}

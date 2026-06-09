package com.hybridrag.rag;

import com.hybridrag.model.Document;
import com.hybridrag.model.QueryContext;
import com.hybridrag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hybrid RAG Engine.
 * <p>
 * Использует гибридный подход:
 * 1. Семантический поиск по embeddings (через Redis vector index).
 * 2. (В плане) Keyword / BM25 поиск — через Redis full-text search.
 * 3. Fusion — объединение результатов из разных retrieval-стратегий.
 */
@Service
public class RagEngine {

    private static final Logger log = LoggerFactory.getLogger(RagEngine.class);
    private static final int TOP_K = 5;
    private static final double MIN_SCORE = 0.5;

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    public RagEngine(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Индексирует документы: чанкинг -> эмбеддинги -> запись в Redis.
     */
    public void indexDocuments(List<Document> documents) {
        embeddingService.embedDocuments(documents);
        vectorStore.storeDocuments(documents);
        log.info("Indexed {} documents", documents.size());
    }

    /**
     * Основной retrieval: берёт query, ищет семантически близкие документы.
     */
    public List<Document> retrieve(String query) {
        var queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding.isEmpty()) {
            log.warn("Empty embedding for query: {}", query);
            return List.of();
        }
        return vectorStore.similaritySearch(queryEmbedding, TOP_K, MIN_SCORE);
    }

    /**
     * Собирает контекст для LLM: историю + ретривнутые документы.
     * Не мутирует ctx — использует только уже установленные документы.
     */
    public String buildRagPrompt(QueryContext ctx) {
        var docs = ctx.getRetrievedDocuments();
        boolean docsMissing = (docs == null || docs.isEmpty());

        if (docsMissing) {
            docs = retrieve(ctx.getQuery());
        }

        var sb = new StringBuilder();
        sb.append(ctx.getSystemPrompt()).append("\n\n");

        if (docs != null && !docs.isEmpty()) {
            sb.append("--- Retrieved Context ---\n");
            for (int i = 0; i < docs.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                    .append(docs.get(i).getSource()).append(": ")
                    .append(docs.get(i).getContent()).append("\n");
            }
            sb.append("--- End of Context ---\n\n");
        }

        sb.append("User question: ").append(ctx.getQuery());
        return sb.toString();
    }
}

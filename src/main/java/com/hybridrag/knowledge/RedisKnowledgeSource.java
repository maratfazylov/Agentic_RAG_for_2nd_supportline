package com.hybridrag.knowledge;

import com.hybridrag.model.Document;
import com.hybridrag.store.VectorStore;
import com.hybridrag.rag.EmbeddingService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisKnowledgeSource implements KnowledgeSource {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    public RedisKnowledgeSource(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    @Override
    public String getName() {
        return "redis-vector";
    }

    @Override
    public List<Document> search(String query, int topK) {
        var embedding = embeddingService.embed(query);
        if (embedding.isEmpty()) return List.of();
        return vectorStore.similaritySearch(embedding, topK, 0.5);
    }
}

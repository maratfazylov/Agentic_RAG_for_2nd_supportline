package com.hybridrag.rag;

import com.hybridrag.llm.LLMService;
import com.hybridrag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final LLMService llmService;

    public EmbeddingService(LLMService llmService) {
        this.llmService = llmService;
    }

    public List<Float> embed(String text) {
        return llmService.embed(text);
    }

    public void embedDocuments(List<Document> documents) {
        var texts = documents.stream()
            .map(Document::getContent)
            .toList();

        var embeddings = llmService.embedBatch(texts);

        for (int i = 0; i < documents.size(); i++) {
            if (i < embeddings.size()) {
                documents.get(i).setEmbedding(embeddings.get(i));
            }
        }

        log.debug("Embedded {} documents", documents.size());
    }
}

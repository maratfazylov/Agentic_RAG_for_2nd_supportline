package com.hybridrag.knowledge;

import com.hybridrag.model.Document;

import java.util.List;

public interface KnowledgeSource {
    String getName();
    List<Document> search(String query, int topK);
}

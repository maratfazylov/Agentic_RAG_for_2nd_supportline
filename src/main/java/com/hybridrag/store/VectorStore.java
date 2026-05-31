package com.hybridrag.store;

import com.hybridrag.model.Document;

import java.util.List;

public interface VectorStore {

    void storeDocument(Document document);

    void storeDocuments(List<Document> documents);

    List<Document> similaritySearch(List<Float> queryEmbedding, int topK);

    List<Document> similaritySearch(List<Float> queryEmbedding, int topK, double scoreThreshold);

    void deleteDocument(String documentId);

    void deleteAll();
}

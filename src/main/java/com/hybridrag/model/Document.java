package com.hybridrag.model;

import java.util.List;
import java.util.Map;

public class Document {
    private String id;
    private String content;
    private String source;
    private List<Float> embedding;
    private Map<String, String> metadata;

    public Document() {}

    public Document(String id, String content, String source) {
        this.id = id;
        this.content = content;
        this.source = source;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<Float> getEmbedding() { return embedding; }
    public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}

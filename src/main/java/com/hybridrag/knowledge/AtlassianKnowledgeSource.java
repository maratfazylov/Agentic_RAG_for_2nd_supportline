package com.hybridrag.knowledge;

import com.hybridrag.model.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AtlassianKnowledgeSource implements KnowledgeSource {

    private final AtlassianMcpClient atlassianClient;

    public AtlassianKnowledgeSource(AtlassianMcpClient atlassianClient) {
        this.atlassianClient = atlassianClient;
    }

    @Override
    public String getName() {
        return "confluence";
    }

    @Override
    public List<Document> search(String query, int topK) {
        return atlassianClient.searchAsDocuments(query, topK);
    }
}

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
        if (atlassianClient instanceof AtlassianMcpStub stub) {
            return stub.searchAsDocuments(query, topK);
        }
        return atlassianClient.searchPages(query, topK).stream()
            .map(p -> {
                var doc = new Document(p.pageId(), p.title() + "\n" + p.body(), "confluence/" + p.spaceKey());
                doc.setMetadata(java.util.Map.of(
                    "pageId", p.pageId(),
                    "spaceKey", p.spaceKey(),
                    "author", p.author(),
                    "lastModified", p.lastModified()
                ));
                return doc;
            })
            .toList();
    }
}

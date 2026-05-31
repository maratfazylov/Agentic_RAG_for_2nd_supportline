package com.hybridrag.knowledge;

import com.hybridrag.model.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AtlassianMcpClient {

    List<ConfluencePage> searchPages(String query, int limit);

    ConfluencePage getPage(String pageId);

    Optional<String> getPageOwner(String topic);

    default List<Document> searchAsDocuments(String query, int limit) {
        return searchPages(query, limit).stream()
            .map(p -> {
                var doc = new Document(p.pageId(), p.title() + "\n" + p.body(), "confluence/" + p.spaceKey());
                doc.setMetadata(Map.of(
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

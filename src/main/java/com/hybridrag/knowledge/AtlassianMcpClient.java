package com.hybridrag.knowledge;

import java.util.List;
import java.util.Optional;

public interface AtlassianMcpClient {

    List<ConfluencePage> searchPages(String query, int limit);

    ConfluencePage getPage(String pageId);

    Optional<String> getPageOwner(String topic);
}

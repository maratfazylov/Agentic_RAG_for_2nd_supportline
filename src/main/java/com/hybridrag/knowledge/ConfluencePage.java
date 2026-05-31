package com.hybridrag.knowledge;

public record ConfluencePage(
    String pageId,
    String title,
    String spaceKey,
    String body,
    String lastModified,
    String author
) {}

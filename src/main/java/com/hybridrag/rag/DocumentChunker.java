package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP = 64;

    public List<Document> chunk(String text, String source) {
        return chunk(text, source, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<Document> chunk(String text, String source, int chunkSize, int overlap) {
        List<Document> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            int chunkEnd = end;
            if (end < text.length()) {
                chunkEnd = findSplitPoint(text, start, end);
            }

            String content = text.substring(start, chunkEnd).trim();
            if (!content.isBlank()) {
                var doc = new Document(UUID.randomUUID().toString(), content, source);
                chunks.add(doc);
            }

            start = chunkEnd - overlap;
            if (start >= text.length() || chunkEnd >= text.length()) break;
        }

        return chunks;
    }

    private int findSplitPoint(String text, int start, int end) {
        int split = text.lastIndexOf('\n', end);
        if (split > start) return split;

        split = text.lastIndexOf('.', end);
        if (split > start) return split + 1;

        split = text.lastIndexOf(' ', end);
        if (split > start) return split + 1;

        return end;
    }

    public List<Document> chunk(List<String> texts, String source) {
        List<Document> all = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            all.addAll(chunk(texts.get(i), source + "_" + i));
        }
        return all;
    }
}

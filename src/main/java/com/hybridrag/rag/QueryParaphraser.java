package com.hybridrag.rag;

import com.hybridrag.llm.LLMService;
import com.hybridrag.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class QueryParaphraser {

    private static final Logger log = LoggerFactory.getLogger(QueryParaphraser.class);

    private final LLMService llmService;

    public QueryParaphraser(LLMService llmService) {
        this.llmService = llmService;
    }

    public List<String> paraphrase(String query, int count) {
        if (count <= 1) return List.of(query);

        try {
            var prompt = buildPrompt(query, count);
            var raw = llmService.chat(List.of(new ChatMessage("user", prompt)));

            var paraphrased = parse(raw, count);
            if (paraphrased.isEmpty()) {
                log.warn("Paraphraser returned empty result for: {}", query);
                return List.of(query);
            }

            log.info("Paraphrased '{}' into {} variants: {}", query, paraphrased.size(), paraphrased);
            return paraphrased;
        } catch (Exception e) {
            log.error("Paraphraser failed: {}", e.getMessage());
            return List.of(query);
        }
    }

    private String buildPrompt(String query, int count) {
        return """
            Generate %d different paraphrased versions of the given question.
            Each version must preserve the original meaning but use different wording.
            Return ONLY a numbered list, one per line, without any additional text.

            Original: %s
            """
            .formatted(count, query);
    }

    private List<String> parse(String response, int expected) {
        var result = new ArrayList<String>();
        for (var line : response.split("\n")) {
            line = line.strip();
            if (line.isBlank()) continue;
            var cleaned = line.replaceAll("^\\d+[\\.\\)]\\s*", "").strip();
            if (cleaned.length() > 5) {
                result.add(cleaned);
            }
        }
        return result.isEmpty() ? List.of(response.strip()) : result;
    }
}

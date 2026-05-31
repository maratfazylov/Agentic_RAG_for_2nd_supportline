package com.hybridrag.routing;

import com.hybridrag.llm.LLMService;
import com.hybridrag.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuestionClassifier {

    private static final Logger log = LoggerFactory.getLogger(QuestionClassifier.class);

    private final LLMService llmService;

    public QuestionClassifier(LLMService llmService) {
        this.llmService = llmService;
    }

    public Classification classify(String question, List<String> knownTopics) {
        return classify(question, knownTopics, List.of());
    }

    public Classification classify(String question, List<String> knownTopics, List<ChatMessage> recentHistory) {
        if (knownTopics.isEmpty()) {
            return new Classification("general", 0.0);
        }

        var prompt = buildClassificationPrompt(question, knownTopics, recentHistory);
        var response = llmService.chat(List.of(new ChatMessage("user", prompt)));

        return parseResponse(response, knownTopics);
    }

    private String buildClassificationPrompt(String question, List<String> topics, List<ChatMessage> history) {
        var sb = new StringBuilder();
        sb.append("Classify the following question into exactly ONE of these topics:\n");
        for (var t : topics) {
            sb.append("- ").append(t).append("\n");
        }
        sb.append("- general\n\n");
        sb.append("Respond ONLY with the topic name and a confidence score (0.0-1.0) in format: topic|score\n");
        sb.append("Example: spark|0.95\n\n");
        if (history != null && !history.isEmpty()) {
            sb.append("Recent conversation context:\n");
            for (var msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Now classify this question: ").append(question);
        return sb.toString();
    }

    private Classification parseResponse(String response, List<String> topics) {
        var parts = response.strip().split("\\|");
        if (parts.length == 2) {
            try {
                var topic = parts[0].strip().toLowerCase();
                var confidence = Double.parseDouble(parts[1].strip());
                if (topics.contains(topic) || topic.equals("general")) {
                    return new Classification(topic, confidence);
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        for (var topic : topics) {
            if (response.toLowerCase().contains(topic)) {
                return new Classification(topic, 0.5);
            }
        }
        return new Classification("general", 0.0);
    }

    public record Classification(String topic, double confidence) {}
}

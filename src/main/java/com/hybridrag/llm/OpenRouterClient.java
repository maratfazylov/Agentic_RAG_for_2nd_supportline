package com.hybridrag.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hybridrag.config.LLMConfig;
import com.hybridrag.model.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OpenRouterClient implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final LLMConfig.LLMProperties props;

    public OpenRouterClient(ObjectMapper mapper, LLMConfig.LLMProperties props) {
        this.mapper = mapper;
        this.props = props;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        return chat(messages, null);
    }

    @Override
    public String chat(List<ChatMessage> messages, String systemPrompt) {
        try {
            var root = mapper.createObjectNode();
            root.put("model", props.model());
            root.put("max_tokens", props.maxTokens());
            root.put("temperature", props.temperature());

            var messagesArray = root.putArray("messages");

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messagesArray.add(chatMessage("system", systemPrompt));
            }

            for (var msg : messages) {
                messagesArray.add(chatMessage(msg.getRole(), msg.getContent()));
            }

            var request = new Request.Builder()
                .url(props.baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(root), JSON))
                .build();

            try (var response = httpClient.newCall(request).execute()) {
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    log.error("OpenRouter API error: {} {}", response.code(),
                        body != null ? body.string() : "no body");
                    return "Sorry, LLM call failed (HTTP " + response.code() + ")";
                }
                var json = mapper.readTree(body.string());
                var choices = json.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.get(0).get("message").get("content").asText();
                }
                return "No response from LLM";
            }
        } catch (IOException e) {
            log.error("OpenRouter chat failed: {}", e.getMessage());
            return "LLM error: " + e.getMessage();
        }
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> results = new ArrayList<>();
        try {
            var root = mapper.createObjectNode();
            root.put("model", props.embeddingModel());
            root.put("encoding_format", "float");
            var input = root.putArray("input");
            texts.forEach(input::add);

            var request = new Request.Builder()
                .url(props.baseUrl() + "/embeddings")
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(root), JSON))
                .build();

            try (var response = httpClient.newCall(request).execute()) {
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    log.error("Embedding API error: {} {}", response.code(),
                        body != null ? body.string() : "no body");
                    return results;
                }
                var json = mapper.readTree(body.string());
                for (var data : json.get("data")) {
                    var vector = new ArrayList<Float>();
                    for (var val : data.get("embedding")) {
                        vector.add(val.floatValue());
                    }
                    results.add(vector);
                }
            }
        } catch (IOException e) {
            log.error("Embedding failed: {}", e.getMessage());
        }
        return results;
    }

    private ObjectNode chatMessage(String role, String content) {
        var node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }
}

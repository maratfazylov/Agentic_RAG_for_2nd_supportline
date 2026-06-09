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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OpenRouterClient implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final LLMConfig.LLMProperties props;

    public OpenRouterClient(ObjectMapper mapper, LLMConfig.LLMProperties props,
                             @Value("${openrouter.connect-timeout-seconds:30}") int connectTimeout,
                             @Value("${openrouter.read-timeout-seconds:60}") int readTimeout,
                             @Value("${openrouter.write-timeout-seconds:30}") int writeTimeout) {
        this.mapper = mapper;
        this.props = props;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        return chat(messages, null);
    }

    @Override
    public String chat(List<ChatMessage> messages, String systemPrompt) {
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

        var requestBody = RequestBody.create(mapper.writeValueAsString(root), JSON);
        var request = new Request.Builder()
            .url(props.baseUrl() + "/chat/completions")
            .header("Authorization", "Bearer " + props.apiKey())
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build();

        String result = retryChatRequest(request);
        return result != null ? result : "Sorry, LLM service is temporarily unavailable";
    }

    private String retryChatRequest(Request request) {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (var response = httpClient.newCall(request).execute()) {
                String parsed = parseChatResponse(response);
                if (parsed != null) return parsed;
                int code = response.code();
                if (code < 500 && code != 429) break;
                log.warn("OpenRouter chat attempt {}/{} returned HTTP {}", attempt, MAX_RETRIES, code);
            } catch (ConnectException | SocketTimeoutException e) {
                lastEx = e;
                log.warn("OpenRouter chat attempt {}/{} timed out: {}", attempt, MAX_RETRIES, e.getMessage());
            } catch (IOException e) {
                lastEx = e;
                log.warn("OpenRouter chat attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                sleep(RETRY_DELAY_MS * attempt);
            }
        }
        log.error("OpenRouter chat failed after {} attempts", MAX_RETRIES, lastEx);
        return null;
    }

    private String parseChatResponse(Response response) throws IOException {
        var body = response.body();
        if (!response.isSuccessful() || body == null) {
            log.error("OpenRouter API error: {} {}", response.code(),
                body != null ? body.string() : "no body");
            return null;
        }
        var json = mapper.readTree(body.string());
        var choices = json.get("choices");
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).get("message").get("content").asText();
        }
        return null;
    }

    @Override
    public List<Float> embed(String text) {
        var results = embedBatch(List.of(text));
        return results.isEmpty() ? Collections.emptyList() : results.getFirst();
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        var root = mapper.createObjectNode();
        root.put("model", props.embeddingModel());
        root.put("encoding_format", "float");
        var input = root.putArray("input");
        texts.forEach(input::add);

        var requestBody = RequestBody.create(mapper.writeValueAsString(root), JSON);
        var request = new Request.Builder()
            .url(props.baseUrl() + "/embeddings")
            .header("Authorization", "Bearer " + props.apiKey())
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build();

        List<List<Float>> result = retryEmbedRequest(request);
        return result != null ? result : Collections.emptyList();
    }

    private List<List<Float>> retryEmbedRequest(Request request) {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (var response = httpClient.newCall(request).execute()) {
                List<List<Float>> parsed = parseEmbedResponse(response);
                if (parsed != null) return parsed;
                int code = response.code();
                if (code < 500 && code != 429) break;
                log.warn("OpenRouter embedding attempt {}/{} returned HTTP {}", attempt, MAX_RETRIES, code);
            } catch (ConnectException | SocketTimeoutException e) {
                lastEx = e;
                log.warn("OpenRouter embedding attempt {}/{} timed out: {}", attempt, MAX_RETRIES, e.getMessage());
            } catch (IOException e) {
                lastEx = e;
                log.warn("OpenRouter embedding attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                sleep(RETRY_DELAY_MS * attempt);
            }
        }
        log.error("OpenRouter embedding failed after {} attempts", MAX_RETRIES, lastEx);
        return null;
    }

    private List<List<Float>> parseEmbedResponse(Response response) throws IOException {
        var body = response.body();
        if (!response.isSuccessful() || body == null) {
            log.error("Embedding API error: {} {}", response.code(),
                body != null ? body.string() : "no body");
            return null;
        }
        var json = mapper.readTree(body.string());
        var results = new ArrayList<List<Float>>();
        for (var data : json.get("data")) {
            var vector = new ArrayList<Float>();
            for (var val : data.get("embedding")) {
                vector.add(val.floatValue());
            }
            results.add(vector);
        }
        return results;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ObjectNode chatMessage(String role, String content) {
        var node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }
}

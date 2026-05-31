package com.hybridrag.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExpertRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExpertRegistry.class);
    private static final String KEY = "registry:experts";

    private final JedisPooled jedis;
    private final ObjectMapper mapper;

    public ExpertRegistry(JedisPooled jedis, ObjectMapper mapper) {
        this.jedis = jedis;
        this.mapper = mapper;
    }

    public void register(String topic, String name, Long chatId, Long telegramChatId, String description) {
        try {
            var expert = new Expert(name, chatId, telegramChatId, description);
            var json = mapper.writeValueAsString(expert);
            jedis.hset(KEY, topic, json);
            log.info("Registered expert '{}' for topic '{}'", name, topic);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize expert: {}", e.getMessage());
        }
    }

    public List<Expert> getExperts(String topic) {
        var json = jedis.hget(KEY, topic);
        if (json == null) return List.of();
        try {
            return List.of(mapper.readValue(json, Expert.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize expert: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> getAllTopics() {
        return new ArrayList<>(jedis.hkeys(KEY));
    }

    public void unregister(String topic) {
        jedis.hdel(KEY, topic);
    }

    public record Expert(String name, Long chatId, Long telegramChatId, String description) {}
}

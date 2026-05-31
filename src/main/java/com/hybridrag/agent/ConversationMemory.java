package com.hybridrag.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybridrag.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);
    private static final String MEMORY_PREFIX = "memory:";
    private static final int TTL_SECONDS = 86400;

    private final JedisPooled jedis;
    private final ObjectMapper mapper;
    private final int maxHistory;

    public ConversationMemory(JedisPooled jedis, ObjectMapper mapper,
                              @Value("${agent.max-history:50}") int maxHistory) {
        this.jedis = jedis;
        this.mapper = mapper;
        this.maxHistory = maxHistory;
    }

    public void addMessage(String userId, ChatMessage message) {
        var key = memoryKey(userId);
        try {
            var msgJson = mapper.writeValueAsString(message);
            jedis.rpush(key, msgJson);
            jedis.expire(key, TTL_SECONDS);

            var len = jedis.llen(key);
            if (len > maxHistory) {
                jedis.lpop(key);
            }
        } catch (Exception e) {
            log.error("Failed to save message for user {}: {}", userId, e.getMessage());
        }
    }

    public List<ChatMessage> getRecent(String userId, int count) {
        var key = memoryKey(userId);
        try {
            var total = jedis.llen(key);
            var start = Math.max(0, total - count);
            var messages = jedis.lrange(key, start, total - 1);
            var result = new ArrayList<ChatMessage>();
            for (var json : messages) {
                result.add(mapper.readValue(json, ChatMessage.class));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to load recent history for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<ChatMessage> getHistory(String userId) {
        var key = memoryKey(userId);
        try {
            var messages = jedis.lrange(key, 0, maxHistory - 1);
            var result = new ArrayList<ChatMessage>();
            for (var json : messages) {
                result.add(mapper.readValue(json, ChatMessage.class));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to load history for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void clearHistory(String userId) {
        jedis.del(memoryKey(userId));
    }

    private String memoryKey(String userId) {
        return MEMORY_PREFIX + userId;
    }
}

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

    private final JedisPooled jedis;
    private final ObjectMapper mapper;
    private final int maxHistory;
    private final String memoryPrefix;
    private final int ttlSeconds;

    /**
     * Lua script: RPUSH + EXPIRE + LTRIM atomically.
     * KEYS[1] = key, ARGV[1] = json, ARGV[2] = ttl, ARGV[3] = maxLen
     */
    private static final String ADD_MESSAGE_LUA = """
        redis.call('RPUSH', KEYS[1], ARGV[1])
        redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
        local len = redis.call('LLEN', KEYS[1])
        local maxLen = tonumber(ARGV[3])
        if len > maxLen then
            redis.call('LTRIM', KEYS[1], len - maxLen, -1)
        end
        return len
        """;

    public ConversationMemory(JedisPooled jedis, ObjectMapper mapper,
                              @Value("${agent.max-history:50}") int maxHistory,
                              @Value("${agent.memory-prefix:memory:}") String memoryPrefix,
                              @Value("${agent.memory-ttl-seconds:86400}") int ttlSeconds) {
        this.jedis = jedis;
        this.mapper = mapper;
        this.maxHistory = maxHistory;
        this.memoryPrefix = memoryPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    public void addMessage(String userId, ChatMessage message) {
        var key = memoryKey(userId);
        try {
            var msgJson = mapper.writeValueAsString(message);
            jedis.eval(ADD_MESSAGE_LUA, List.of(key), List.of(msgJson, String.valueOf(ttlSeconds), String.valueOf(maxHistory)));
        } catch (Exception e) {
            log.error("Failed to save message for user {}", userId, e);
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
            log.error("Failed to load recent history for user {}", userId, e);
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
            log.error("Failed to load history for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    public void clearHistory(String userId) {
        jedis.del(memoryKey(userId));
    }

    private String memoryKey(String userId) {
        return memoryPrefix + userId;
    }
}

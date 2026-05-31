package com.hybridrag.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

@Component
public class AnswerTracker {

    private static final Logger log = LoggerFactory.getLogger(AnswerTracker.class);
    private static final long QUESTION_TTL = 86400;
    private static final String QUESTION_PREFIX = "question:";
    private static final String ACTIVITY_PREFIX = "activity:";
    private static final String LAST_SEEN_PREFIX = "lastSeen:";

    private final JedisPooled jedis;

    public AnswerTracker(JedisPooled jedis) {
        this.jedis = jedis;
    }

    public void trackQuestion(Long chatId, Integer messageId, String topic) {
        try {
            var key = QUESTION_PREFIX + chatId + ":" + messageId;
            jedis.setex(key, QUESTION_TTL, topic);
            log.debug("Tracked question {} in chat {} as topic '{}'", messageId, chatId, topic);
        } catch (Exception e) {
            log.error("Failed to track question: {}", e.getMessage());
        }
    }

    public void trackAnswer(Long chatId, Integer replyToMessageId, String answererUsername) {
        try {
            var questionKey = QUESTION_PREFIX + chatId + ":" + replyToMessageId;
            var topic = jedis.get(questionKey);
            if (topic == null) {
                log.debug("No tracked question for replyToMessageId={} in chat {}", replyToMessageId, chatId);
                return;
            }

            var activityKey = ACTIVITY_PREFIX + topic;
            jedis.zincrby(activityKey, 1, answererUsername);

            var lastSeenKey = LAST_SEEN_PREFIX + topic + ":" + answererUsername;
            jedis.setex(lastSeenKey, 86400 * 30, String.valueOf(System.currentTimeMillis()));

            log.info("Answer tracked: user '{}' +1 on topic '{}'", answererUsername, topic);
        } catch (Exception e) {
            log.error("Failed to track answer: {}", e.getMessage());
        }
    }

    public List<ScoredExpert> getTopExperts(String topic, int topN) {
        var activityKey = ACTIVITY_PREFIX + topic;
        try {
            var result = jedis.zrevrangeWithScores(activityKey, 0, topN - 1);
            var experts = new ArrayList<ScoredExpert>();
            for (var tuple : result) {
                var username = tuple.getElement();
                var lastSeenKey = LAST_SEEN_PREFIX + topic + ":" + username;
                if (jedis.exists(lastSeenKey)) {
                    experts.add(new ScoredExpert(username, tuple.getScore()));
                }
            }
            return experts;
        } catch (Exception e) {
            log.error("Failed to get top experts: {}", e.getMessage());
            return List.of();
        }
    }

    public record ScoredExpert(String username, double score) {}
}

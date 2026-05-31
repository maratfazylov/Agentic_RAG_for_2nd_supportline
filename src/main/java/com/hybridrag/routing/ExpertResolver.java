package com.hybridrag.routing;

import com.hybridrag.knowledge.AtlassianMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ExpertResolver {

    private static final Logger log = LoggerFactory.getLogger(ExpertResolver.class);

    private final AnswerTracker answerTracker;
    private final AtlassianMcpClient atlassianClient;
    private final ExpertRegistry expertRegistry;
    private final ExpertProperties properties;

    public ExpertResolver(AnswerTracker answerTracker, AtlassianMcpClient atlassianClient,
                          ExpertRegistry expertRegistry, ExpertProperties properties) {
        this.answerTracker = answerTracker;
        this.atlassianClient = atlassianClient;
        this.expertRegistry = expertRegistry;
        this.properties = properties;
    }

    public List<String> getAllTopics() {
        return expertRegistry.getAllTopics();
    }

    public Optional<ExpertRegistry.Expert> resolve(String topic) {
        // 1. Activity — top expert with score >= threshold
        var topExperts = answerTracker.getTopExperts(topic, 3);
        for (var se : topExperts) {
            if (se.score() >= properties.getActivity().getThreshold()) {
                log.info("Resolved via activity: {} (score={}) for topic '{}'", se.username(), se.score(), topic);
                return Optional.of(new ExpertRegistry.Expert(se.username(), null, null, "activity"));
            }
        }

        // 2. Atlassian page owner
        var owner = atlassianClient.getPageOwner(topic);
        if (owner.isPresent()) {
            log.info("Resolved via Confluence page owner: {} for topic '{}'", owner.get(), topic);
            return Optional.of(new ExpertRegistry.Expert(owner.get(), null, null, "confluence owner"));
        }

        // 3. Static ExpertRegistry fallback
        var staticExperts = expertRegistry.getExperts(topic);
        if (!staticExperts.isEmpty()) {
            var expert = staticExperts.getFirst();
            log.info("Resolved via static registry: {} for topic '{}'", expert.name(), topic);
            return Optional.of(expert);
        }

        log.info("No expert found for topic '{}'", topic);
        return Optional.empty();
    }
}

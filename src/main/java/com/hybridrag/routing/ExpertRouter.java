package com.hybridrag.routing;

import com.hybridrag.model.QueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExpertRouter {

    private static final Logger log = LoggerFactory.getLogger(ExpertRouter.class);

    private final QuestionClassifier classifier;
    private final ExpertResolver expertResolver;
    private final ExpertProperties properties;

    public ExpertRouter(QuestionClassifier classifier, ExpertResolver expertResolver,
                        ExpertProperties properties) {
        this.classifier = classifier;
        this.expertResolver = expertResolver;
        this.properties = properties;
    }

    public RouteResult route(QueryContext ctx) {
        var classification = classifier.classify(ctx.getQuery(), expertResolver.getAllTopics(), ctx.getConversationHistory());

        if (classification.confidence() < properties.getRouting().getThreshold()
            || "general".equals(classification.topic())) {
            return new RouteResult(RouteAction.ANSWER_DIRECTLY, null, classification);
        }

        var resolved = expertResolver.resolve(classification.topic());
        if (resolved.isEmpty()) {
            return new RouteResult(RouteAction.ANSWER_DIRECTLY, null, classification);
        }

        var expert = resolved.get();
        log.info("Routing '{}' (topic={}, confidence={}) to expert {}",
            ctx.getQuery(), classification.topic(),
            String.format("%.2f", classification.confidence()), expert.name());

        return new RouteResult(RouteAction.ROUTE_TO_EXPERT, expert, classification);
    }

    public enum RouteAction {
        ANSWER_DIRECTLY,
        ROUTE_TO_EXPERT
    }

    public record RouteResult(RouteAction action, ExpertRegistry.Expert expert, QuestionClassifier.Classification classification) {}
}

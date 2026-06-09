package com.hybridrag.routing;

import com.hybridrag.model.QueryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertRouterTest {

    @Mock
    private QuestionClassifier classifier;
    @Mock
    private ExpertResolver expertResolver;
    @Mock
    private ExpertProperties properties;
    @Mock
    private ExpertProperties.Routing routing;
    @Mock
    private ExpertProperties.Activity activity;

    private ExpertRouter router;

    @BeforeEach
    void setUp() {
        router = new ExpertRouter(classifier, expertResolver, properties);
    }

    private QueryContext ctx(String query) {
        var ctx = new QueryContext();
        ctx.setQuery(query);
        ctx.setUserId("user1");
        ctx.setUsername("testuser");
        ctx.setChatId(123L);
        return ctx;
    }

    @Nested
    @DisplayName("Route action decisions")
    class RouteDecisions {

        @Test
        void shouldAnswerDirectlyWhenBelowThreshold() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            when(classifier.classify(anyString(), any(), any()))
                .thenReturn(new QuestionClassifier.Classification("kafka", 0.3));
            when(expertResolver.getAllTopics()).thenReturn(List.of("kafka"));

            var result = router.route(ctx("Kafka question"));

            assertThat(result.action()).isEqualTo(ExpertRouter.RouteAction.ANSWER_DIRECTLY);
            assertThat(result.expert()).isNull();
            verifyNoInteractions(expertResolver);
        }

        @Test
        void shouldAnswerDirectlyForGeneralTopic() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            when(classifier.classify(anyString(), any(), any()))
                .thenReturn(new QuestionClassifier.Classification("general", 0.9));
            when(expertResolver.getAllTopics()).thenReturn(List.of("kafka"));

            var result = router.route(ctx("General question"));

            assertThat(result.action()).isEqualTo(ExpertRouter.RouteAction.ANSWER_DIRECTLY);
        }

        @Test
        void shouldRouteToExpertWhenConfidenceHigh() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            when(classifier.classify(anyString(), any(), any()))
                .thenReturn(new QuestionClassifier.Classification("kafka", 0.9));
            when(expertResolver.getAllTopics()).thenReturn(List.of("kafka"));

            var expert = new ExpertRegistry.Expert("alice", null, null, "registry");
            when(expertResolver.resolve("kafka")).thenReturn(Optional.of(expert));

            var result = router.route(ctx("Kafka question"));

            assertThat(result.action()).isEqualTo(ExpertRouter.RouteAction.ROUTE_TO_EXPERT);
            assertThat(result.expert()).isNotNull();
            assertThat(result.expert().name()).isEqualTo("alice");
        }

        @Test
        void shouldAnswerDirectlyWhenNoExpertFound() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            when(classifier.classify(anyString(), any(), any()))
                .thenReturn(new QuestionClassifier.Classification("kafka", 0.9));
            when(expertResolver.getAllTopics()).thenReturn(List.of("kafka"));
            when(expertResolver.resolve("kafka")).thenReturn(Optional.empty());

            var result = router.route(ctx("Kafka question"));

            assertThat(result.action()).isEqualTo(ExpertRouter.RouteAction.ANSWER_DIRECTLY);
        }

        @Test
        void shouldRouteAtExactThreshold() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            when(classifier.classify(anyString(), any(), any()))
                .thenReturn(new QuestionClassifier.Classification("kafka", 0.6));
            when(expertResolver.getAllTopics()).thenReturn(List.of("kafka"));

            var expert = new ExpertRegistry.Expert("bob", null, null, "registry");
            when(expertResolver.resolve("kafka")).thenReturn(Optional.of(expert));

            var result = router.route(ctx("Question at threshold"));

            assertThat(result.action()).isEqualTo(ExpertRouter.RouteAction.ROUTE_TO_EXPERT);
        }
    }

    @Nested
    @DisplayName("Classification metadata")
    class ClassificationMetadata {

        @Test
        void shouldPreserveClassificationInResult() {
            when(properties.getRouting()).thenReturn(routing);
            when(routing.getThreshold()).thenReturn(0.6);
            var classification = new QuestionClassifier.Classification("spark", 0.85);
            when(classifier.classify(anyString(), any(), any())).thenReturn(classification);
            when(expertResolver.getAllTopics()).thenReturn(List.of("spark"));

            var result = router.route(ctx("Spark question?"));

            assertThat(result.classification()).isEqualTo(classification);
        }
    }
}

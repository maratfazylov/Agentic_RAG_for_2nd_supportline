package com.hybridrag.routing;

import com.hybridrag.knowledge.AtlassianMcpClient;
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
class ExpertResolverTest {

    @Mock
    private AnswerTracker answerTracker;
    @Mock
    private AtlassianMcpClient atlassianClient;
    @Mock
    private ExpertRegistry expertRegistry;
    @Mock
    private ExpertProperties properties;
    @Mock
    private ExpertProperties.Activity activity;

    private ExpertResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExpertResolver(answerTracker, atlassianClient, expertRegistry, properties);
        lenient().when(properties.getActivity()).thenReturn(activity);
        lenient().when(activity.getThreshold()).thenReturn(5);
    }

    @Nested
    @DisplayName("Resolution chain")
    class ResolutionChain {

        @Test
        void shouldResolveViaActivityWhenScoreAboveThreshold() {
            when(answerTracker.getTopExperts("kafka", 3))
                .thenReturn(List.of(new AnswerTracker.ScoredExpert("alice", 7.0)));

            var result = resolver.resolve("kafka");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("alice");
            assertThat(result.get().description()).isEqualTo("activity-based");
            verifyNoInteractions(atlassianClient, expertRegistry);
        }

        @Test
        void shouldSkipActivityWhenScoreBelowThreshold() {
            when(answerTracker.getTopExperts("kafka", 3))
                .thenReturn(List.of(new AnswerTracker.ScoredExpert("alice", 2.0)));
            when(atlassianClient.getPageOwner("kafka"))
                .thenReturn(Optional.of("bob"));

            var result = resolver.resolve("kafka");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("bob");
            assertThat(result.get().description()).isEqualTo("confluence owner");
            verifyNoInteractions(expertRegistry);
        }

        @Test
        void shouldFallBackToAtlassian() {
            when(answerTracker.getTopExperts(anyString(), anyInt()))
                .thenReturn(List.of());
            when(atlassianClient.getPageOwner("kafka"))
                .thenReturn(Optional.of("bob"));

            var result = resolver.resolve("kafka");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("bob");
        }

        @Test
        void shouldFallBackToStaticRegistry() {
            when(answerTracker.getTopExperts(anyString(), anyInt()))
                .thenReturn(List.of());
            when(atlassianClient.getPageOwner("kafka"))
                .thenReturn(Optional.empty());
            var expert = new ExpertRegistry.Expert("charlie", null, null, "registry");
            when(expertRegistry.getExperts("kafka"))
                .thenReturn(List.of(expert));

            var result = resolver.resolve("kafka");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("charlie");
        }

        @Test
        void shouldReturnEmptyWhenAllMethodsFail() {
            when(answerTracker.getTopExperts(anyString(), anyInt()))
                .thenReturn(List.of());
            when(atlassianClient.getPageOwner("kafka"))
                .thenReturn(Optional.empty());
            when(expertRegistry.getExperts("kafka"))
                .thenReturn(List.of());

            var result = resolver.resolve("kafka");

            assertThat(result).isEmpty();
        }

        @Test
        void activityTakesPriorityOverHigherScoreAtlassian() {
            // Both resolve, but activity wins
            when(answerTracker.getTopExperts("kafka", 3))
                .thenReturn(List.of(
                    new AnswerTracker.ScoredExpert("low", 3.0),
                    new AnswerTracker.ScoredExpert("high", 8.0)
                ));
            when(atlassianClient.getPageOwner("kafka"))
                .thenReturn(Optional.of("pageowner"));

            var result = resolver.resolve("kafka");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("high");
        }
    }

    @Nested
    @DisplayName("Topic listing")
    class TopicListing {

        @Test
        void shouldGetAllTopicsFromRegistry() {
            when(expertRegistry.getAllTopics())
                .thenReturn(List.of("kafka", "spark", "redis"));

            var topics = resolver.getAllTopics();

            assertThat(topics).containsExactly("kafka", "spark", "redis");
        }
    }
}

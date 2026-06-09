package com.hybridrag.routing;

import com.hybridrag.llm.LLMService;
import com.hybridrag.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionClassifierTest {

    @Mock
    private LLMService llmService;

    private QuestionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new QuestionClassifier(llmService);
    }

    @Nested
    @DisplayName("Classification with known topics")
    class KnownTopics {

        @Test
        void shouldClassifyWithValidFormat() {
            when(llmService.chat(any())).thenReturn("kafka|0.95");

            var result = classifier.classify("How to tune Kafka?", List.of("kafka", "spark"));

            assertThat(result.topic()).isEqualTo("kafka");
            assertThat(result.confidence()).isEqualTo(0.95);
        }

        @Test
        void shouldParseTopicFromResponse() {
            when(llmService.chat(any())).thenReturn("spark|0.8");

            var result = classifier.classify("Spark tuning", List.of("kafka", "spark"));

            assertThat(result.topic()).isEqualTo("spark");
            assertThat(result.confidence()).isEqualTo(0.8);
        }

        @Test
        void shouldFallBackToKeywordMatching() {
            when(llmService.chat(any())).thenReturn("The topic is kafka I think");

            var result = classifier.classify("Some Kafka question", List.of("kafka", "spark"));

            assertThat(result.topic()).isEqualTo("kafka");
            assertThat(result.confidence()).isEqualTo(0.5);
        }

        @Test
        void shouldReturnGeneralForLowConfidence() {
            when(llmService.chat(any())).thenReturn("nonsense response");

            var result = classifier.classify("random text", List.of("kafka", "spark"));

            assertThat(result.topic()).isEqualTo("general");
            assertThat(result.confidence()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        void shouldReturnGeneralForEmptyTopics() {
            var result = classifier.classify("Any question", List.of());

            assertThat(result.topic()).isEqualTo("general");
            assertThat(result.confidence()).isEqualTo(0.0);
            verifyNoInteractions(llmService);
        }

        @Test
        void shouldHandleMalformedConfidence() {
            when(llmService.chat(any())).thenReturn("kafka|notanumber");

            var result = classifier.classify("Question", List.of("kafka"));

            // Falls through to keyword matching
            assertThat(result.topic()).isEqualTo("kafka");
            assertThat(result.confidence()).isEqualTo(0.5);
        }

        @Test
        void shouldHandleTopicNotInList() {
            when(llmService.chat(any())).thenReturn("unknown_topic|0.9");

            var result = classifier.classify("Question", List.of("kafka"));

            // Unknown topic → general via keyword fallback won't match either
            assertThat(result.topic()).isEqualTo("general");
            assertThat(result.confidence()).isEqualTo(0.0);
        }

        @Test
        void shouldAcceptGeneralTopic() {
            when(llmService.chat(any())).thenReturn("general|0.6");

            var result = classifier.classify("Generic question", List.of("kafka"));

            assertThat(result.topic()).isEqualTo("general");
            assertThat(result.confidence()).isEqualTo(0.6);
        }
    }

    @Nested
    @DisplayName("Prompt building")
    class PromptBuilding {

        @Test
        void shouldIncludeHistoryInPrompt() {
            var history = List.of(
                new ChatMessage("user", "prev question"),
                new ChatMessage("assistant", "prev answer")
            );
            when(llmService.chat(any())).thenReturn("kafka|0.8");

            classifier.classify("New question", List.of("kafka"), history);

            verify(llmService).chat(argThat(msgs ->
                msgs.size() == 1 && msgs.get(0).getContent().contains("prev question")
            ));
        }
    }
}

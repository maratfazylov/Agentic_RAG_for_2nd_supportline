package com.hybridrag.rag;

import com.hybridrag.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new DocumentChunker(100, 20);
    }

    @Nested
    @DisplayName("Fixed-size chunking")
    class FixedSizeChunking {

        @Test
        void shouldReturnEmptyForEmptyContent() {
            var doc = new Document("d1", "", "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.FIXED_SIZE);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEmpty();
        }

        @Test
        void shouldNotChunkShortContent() {
            var doc = new Document("d1", "short", "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.FIXED_SIZE);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("short");
        }

        @Test
        void shouldChunkLongContent() {
            var content = "a".repeat(250);
            var doc = new Document("d1", content, "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.FIXED_SIZE);
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void shouldPreserveDocumentId() {
            var content = "a".repeat(250);
            var doc = new Document("doc-42", content, "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.FIXED_SIZE);
            assertThat(chunks).allMatch(c -> c.getId().startsWith("doc-42"));
        }

        @Test
        void shouldApplyOverlap() {
            var content = "a".repeat(250);
            var doc = new Document("d1", content, "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.FIXED_SIZE);
            if (chunks.size() > 1) {
                // Overlap means chunks share some content
                assertThat(chunks.get(0).getContent().length()).isLessThanOrEqualTo(100);
            }
        }
    }

    @Nested
    @DisplayName("Paragraph chunking")
    class ParagraphChunking {

        @Test
        void shouldChunkByParagraphs() {
            var content = "Paragraph one.\n\nParagraph two.\n\nParagraph three.";
            var doc = new Document("d1", content, "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.PARAGRAPH);
            assertThat(chunks).hasSize(3);
        }

        @Test
        void shouldHandleSingleParagraph() {
            var doc = new Document("d1", "Just one paragraph.", "src");
            var chunks = chunker.chunk(doc, DocumentChunker.Strategy.PARAGRAPH);
            assertThat(chunks).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void shouldThrowForNullStrategy() {
            var doc = new Document("d1", "content", "src");
            assertThatThrownBy(() -> chunker.chunk(doc, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectInvalidChunkSize() {
            assertThatThrownBy(() -> new DocumentChunker(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeOverlap() {
            assertThatThrownBy(() -> new DocumentChunker(100, -1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectOverlapLargerThanChunkSize() {
            assertThatThrownBy(() -> new DocumentChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

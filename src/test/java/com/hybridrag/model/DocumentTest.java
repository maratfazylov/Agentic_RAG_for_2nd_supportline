package com.hybridrag.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTest {

    @Test
    @DisplayName("Should create document with all-args constructor")
    void shouldCreateWithAllArgs() {
        var doc = new Document("id1", "content", "confluence");
        assertThat(doc.getId()).isEqualTo("id1");
        assertThat(doc.getContent()).isEqualTo("content");
        assertThat(doc.getSource()).isEqualTo("confluence");
    }

    @Test
    @DisplayName("Should create empty document with default constructor")
    void shouldCreateEmpty() {
        var doc = new Document();
        assertThat(doc.getId()).isNull();
        assertThat(doc.getContent()).isNull();
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetProperties() {
        var doc = new Document();
        doc.setId("id2");
        doc.setContent("test content");
        doc.setSource("redis");
        doc.setEmbedding(List.of(0.1f, 0.2f, 0.3f));
        doc.setMetadata(Map.of("key", "value"));

        assertThat(doc.getId()).isEqualTo("id2");
        assertThat(doc.getContent()).isEqualTo("test content");
        assertThat(doc.getSource()).isEqualTo("redis");
        assertThat(doc.getEmbedding()).hasSize(3);
        assertThat(doc.getMetadata()).containsEntry("key", "value");
    }
}

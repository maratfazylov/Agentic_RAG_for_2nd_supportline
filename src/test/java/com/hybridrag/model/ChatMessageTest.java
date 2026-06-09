package com.hybridrag.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    @Test
    @DisplayName("Should create message with role and content")
    void shouldCreateWithArgs() {
        var msg = new ChatMessage("user", "hello");
        assertThat(msg.getRole()).isEqualTo("user");
        assertThat(msg.getContent()).isEqualTo("hello");
        assertThat(msg.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should auto-set timestamp")
    void shouldAutoSetTimestamp() {
        var before = Instant.now();
        var msg = new ChatMessage("assistant", "reply");
        var after = Instant.now();

        assertThat(msg.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should create empty message")
    void shouldCreateEmpty() {
        var msg = new ChatMessage();
        assertThat(msg.getRole()).isNull();
        assertThat(msg.getContent()).isNull();
        assertThat(msg.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("Should set properties")
    void shouldSetProperties() {
        var msg = new ChatMessage();
        msg.setRole("system");
        msg.setContent("prompt");
        var ts = Instant.parse("2025-01-01T00:00:00Z");
        msg.setTimestamp(ts);

        assertThat(msg.getRole()).isEqualTo("system");
        assertThat(msg.getContent()).isEqualTo("prompt");
        assertThat(msg.getTimestamp()).isEqualTo(ts);
    }
}

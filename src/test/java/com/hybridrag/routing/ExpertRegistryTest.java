package com.hybridrag.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisPooled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertRegistryTest {

    @Mock
    private JedisPooled jedis;

    private ExpertRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        registry = new ExpertRegistry(jedis, mapper);
    }

    @Nested
    @DisplayName("Register and retrieve")
    class RegisterAndRetrieve {

        @Test
        void shouldRegisterExpert() throws Exception {
            registry.register("kafka", "alice", 111L, 222L, "Kafka expert");

            verify(jedis).hset(eq("registry:experts"), eq("kafka"), anyString());
        }

        @Test
        void shouldRetrieveExpert() throws Exception {
            var json = mapper.writeValueAsString(
                new ExpertRegistry.Expert("alice", 111L, 222L, "Kafka expert"));
            when(jedis.hget("registry:experts", "kafka")).thenReturn(json);

            var experts = registry.getExperts("kafka");

            assertThat(experts).hasSize(1);
            assertThat(experts.get(0).name()).isEqualTo("alice");
            assertThat(experts.get(0).telegramChatId()).isEqualTo(222L);
        }

        @Test
        void shouldReturnEmptyForMissingTopic() {
            when(jedis.hget("registry:experts", "nonexistent")).thenReturn(null);

            var experts = registry.getExperts("nonexistent");

            assertThat(experts).isEmpty();
        }

        @Test
        void shouldReturnAllTopics() {
            when(jedis.hkeys("registry:experts"))
                .thenReturn(List.of("kafka", "spark", "redis"));

            var topics = registry.getAllTopics();

            assertThat(topics).containsExactly("kafka", "spark", "redis");
        }

        @Test
        void shouldFindChatIdByUsername() throws Exception {
            var json = mapper.writeValueAsString(
                new ExpertRegistry.Expert("alice", 111L, 222L, "expert"));
            when(jedis.hkeys("registry:experts")).thenReturn(List.of("kafka"));
            when(jedis.hget("registry:experts", "kafka")).thenReturn(json);

            var chatId = registry.getChatId("alice");

            assertThat(chatId).isEqualTo(222L);
        }

        @Test
        void shouldReturnNullWhenUsernameNotFound() throws Exception {
            var json = mapper.writeValueAsString(
                new ExpertRegistry.Expert("alice", 111L, 222L, "expert"));
            when(jedis.hkeys("registry:experts")).thenReturn(List.of("kafka"));
            when(jedis.hget("registry:experts", "kafka")).thenReturn(json);

            var chatId = registry.getChatId("bob");

            assertThat(chatId).isNull();
        }
    }

    @Nested
    @DisplayName("Unregister")
    class Unregister {

        @Test
        void shouldRemoveTopic() {
            registry.unregister("kafka");
            verify(jedis).hdel("registry:experts", "kafka");
        }
    }
}

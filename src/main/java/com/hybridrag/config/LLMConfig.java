package com.hybridrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.model}")
    private String model;

    @Value("${openrouter.base-url}")
    private String baseUrl;

    @Value("${openrouter.max-tokens}")
    private int maxTokens;

    @Value("${openrouter.temperature}")
    private double temperature;

    @Value("${embedding.model}")
    private String embeddingModel;

    @Value("${embedding.dimension}")
    private int embeddingDimension;

    @Bean
    public LLMProperties llmProperties() {
        return new LLMProperties(apiKey, model, baseUrl, maxTokens, temperature, embeddingModel, embeddingDimension);
    }

    public record LLMProperties(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens,
        double temperature,
        String embeddingModel,
        int embeddingDimension
    ) {}
}

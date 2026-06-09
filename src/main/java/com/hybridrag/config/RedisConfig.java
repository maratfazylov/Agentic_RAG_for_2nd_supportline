package com.hybridrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private int port;

    @Value("${redis.password}")
    private String password;

    @Value("${redis.pool.max-total:16}")
    private int poolMaxTotal;

    @Value("${redis.pool.max-idle:8}")
    private int poolMaxIdle;

    @Value("${redis.pool.min-idle:2}")
    private int poolMinIdle;

    @Value("${redis.timeout-ms:2000}")
    private int timeoutMs;

    @Bean
    public JedisPooled jedisPooled() {
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(poolMaxTotal);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);

        if (password != null && !password.isBlank()) {
            return new JedisPooled(poolConfig, host, port, timeoutMs, password);
        }
        return new JedisPooled(poolConfig, host, port, timeoutMs);
    }
}

package com.hybridrag.tool;

import com.hybridrag.model.QueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Пример тула для получения погоды.
 * Пока возвращает заглушку — позже подключим OpenWeatherMap API.
 */
@Component
public class WeatherTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private final ToolRegistry registry;

    public WeatherTool(ToolRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "Get current weather for a city. Usage: /weather(city name)";
    }

    @Override
    public String execute(String args, QueryContext ctx) {
        log.info("Weather tool called with args: {}", args);
        // TODO: реальный вызов OpenWeatherMap API
        return "Weather in '" + args + "': +18°C, partly cloudy";
    }
}

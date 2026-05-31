package com.hybridrag.tool;

import com.hybridrag.model.QueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Tool> tools = new HashMap<>();

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        log.info("Registered tool: {} - {}", tool.getName(), tool.getDescription());
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public List<Tool> getAllTools() {
        return List.copyOf(tools.values());
    }

    public String getToolsDescription() {
        var sb = new StringBuilder("Available tools:\n");
        tools.values().forEach(t ->
            sb.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append("\n"));
        return sb.toString();
    }

    /**
     * Парсит ответ LLM на предмет вызова tools и исполняет их.
     * Пока простая заглушка — /tool_name(args).
     * В будущем — function calling через OpenRouter.
     */
    public String executeTools(String llmResponse, QueryContext ctx) {
        var result = new StringBuilder();
        for (var entry : tools.entrySet()) {
            var pattern = "/" + entry.getKey() + "(";
            int start = llmResponse.indexOf(pattern);
            if (start >= 0) {
                int end = llmResponse.indexOf(')', start);
                if (end > start) {
                    var args = llmResponse.substring(start + pattern.length(), end);
                    log.info("Executing tool {} with args: {}", entry.getKey(), args);
                    var toolResult = entry.getValue().execute(args, ctx);
                    result.append(toolResult).append("\n");
                }
            }
        }
        return result.isEmpty() ? null : result.toString();
    }
}

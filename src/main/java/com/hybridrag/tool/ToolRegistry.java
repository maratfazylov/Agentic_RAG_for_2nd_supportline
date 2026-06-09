package com.hybridrag.tool;

import com.hybridrag.model.QueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("/([a-zA-Z0-9_]+)\\(([^)]*)\\)");

    /**
     * Парсит ответ LLM на предмет вызова tools и исполняет их.
     * Формат: /tool_name(args).
     * В будущем — function calling через OpenRouter.
     */
    public String executeTools(String llmResponse, QueryContext ctx) {
        if (llmResponse == null || llmResponse.isBlank()) return null;

        var result = new StringBuilder();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            var toolName = matcher.group(1);
            var args = matcher.group(2).trim();
            var tool = tools.get(toolName);
            if (tool != null) {
                log.info("Executing tool {} with args: {}", toolName, args);
                var toolResult = tool.execute(args, ctx);
                if (toolResult != null) {
                    result.append(toolResult).append("\n");
                }
            } else {
                log.warn("LLM tried to call unknown tool: {}", toolName);
            }
        }
        return result.isEmpty() ? null : result.toString().trim();
    }
}

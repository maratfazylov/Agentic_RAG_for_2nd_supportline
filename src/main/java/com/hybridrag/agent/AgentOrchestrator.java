package com.hybridrag.agent;

import com.hybridrag.bot.TelegramBot;
import com.hybridrag.llm.LLMService;
import com.hybridrag.model.ChatMessage;
import com.hybridrag.model.QueryContext;
import com.hybridrag.rag.RagEngine;
import com.hybridrag.rag.RagFusion;
import com.hybridrag.routing.AnswerTracker;
import com.hybridrag.routing.ExpertRegistry;
import com.hybridrag.routing.ExpertRouter;
import com.hybridrag.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final int FUSION_TOP_K = 10;

    private final LLMService llmService;
    private final RagEngine ragEngine;
    private final RagFusion ragFusion;
    private final ConversationMemory memory;
    private final ToolRegistry toolRegistry;
    private final ExpertRouter expertRouter;
    private final AnswerTracker answerTracker;
    private final ExpertRegistry expertRegistry;
    private final TelegramBot telegramBot;

    @Value("${agent.system-prompt}")
    private String systemPrompt;

    public AgentOrchestrator(LLMService llmService, RagEngine ragEngine,
                             RagFusion ragFusion, ConversationMemory memory,
                             ToolRegistry toolRegistry, ExpertRouter expertRouter,
                             AnswerTracker answerTracker,
                             ExpertRegistry expertRegistry,
                             @Lazy TelegramBot telegramBot) {
        this.llmService = llmService;
        this.ragEngine = ragEngine;
        this.ragFusion = ragFusion;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.expertRouter = expertRouter;
        this.answerTracker = answerTracker;
        this.expertRegistry = expertRegistry;
        this.telegramBot = telegramBot;
    }

    public String handle(String userId, Long chatId, String query) {
        return handle(userId, chatId, query, "unknown");
    }

    public String handle(String userId, Long chatId, String query, String username) {
        try {
            return handleInternal(userId, chatId, query, username);
        } catch (Exception e) {
            log.error("Error handling query from user={}: {}", userId, query, e);
            return "Не удалось обработать запрос. Попробуйте переформулировать вопрос.";
        }
    }

    private String handleInternal(String userId, Long chatId, String query, String username) {
        log.info("Handling query from user={} ({}): {}", userId, username, query);

        // 1. RAG Fusion: paraphrase → N×retrieve → RRF
        var fusion = ragFusion.search(query, FUSION_TOP_K);
        var mergedDocs = fusion.mergedDocuments();

        // 2. Подготовка контекста
        var ctx = new QueryContext();
        ctx.setQuery(query);
        ctx.setUserId(userId);
        ctx.setUsername(username);
        ctx.setChatId(chatId);
        ctx.setRetrievedDocuments(mergedDocs);
        ctx.setSystemPrompt(systemPrompt);

        // 3. История диалога
        var history = memory.getHistory(userId);
        ctx.setConversationHistory(history);

        // 4. Expert routing
        var route = expertRouter.route(ctx);
        if (route.action() == ExpertRouter.RouteAction.ROUTE_TO_EXPERT) {
            var expert = route.expert();
            log.info("Routing to expert {} (topic={})", expert.name(), route.classification().topic());
            var chatId = expertRegistry.getChatId(expert.name());
            if (chatId == null) {
                telegramBot.sendToChat(ctx.getChatId(),
                    "Вопрос для @" + expert.name() + " по теме " + route.classification().topic()
                    + ". Эксперт будет уведомлён.");
                return "Эксперт @" + expert.name() + " будет уведомлён о вашем вопросе.";
            }
            telegramBot.forwardToExpert(
                chatId,
                ctx.getQuery(),
                ctx.getUsername(),
                route.classification().topic()
            );
            return "Forwarded question to expert on " + route.classification().topic()
                + " — @" + expert.name() + ". Usually responds within an hour.";
        }

        // 5. Сборка prompt (Fusion или fallback)
        var ragPrompt = (mergedDocs.isEmpty() || fusion.perQueryResults().size() <= 1)
            ? ragEngine.buildRagPrompt(ctx)
            : ragFusion.buildFusionPrompt(query, fusion);
        ctx.setRetrievedDocuments(mergedDocs);

        // 6. Подготовка сообщений для LLM
        var messages = new ArrayList<ChatMessage>();
        for (var msg : history) {
            messages.add(msg);
        }
        messages.add(new ChatMessage("user", ragPrompt));

        // 7. Вызов LLM
        var llmResponse = llmService.chat(messages, systemPrompt);

        // 8. Tool calling
        var toolResult = toolRegistry.executeTools(llmResponse, ctx);
        if (toolResult != null) {
            var toolMessages = new ArrayList<>(messages);
            toolMessages.add(new ChatMessage("assistant", llmResponse));
            toolMessages.add(new ChatMessage("user",
                "Tool result: " + toolResult + "\nPlease answer based on this result."));
            llmResponse = llmService.chat(toolMessages, systemPrompt);
        }

        // 9. Сохраняем историю
        memory.addMessage(userId, new ChatMessage("user", query));
        memory.addMessage(userId, new ChatMessage("assistant", llmResponse));

        log.info("Response to user={}: {}...", userId,
            llmResponse.substring(0, Math.min(100, llmResponse.length())));
        return llmResponse;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
}

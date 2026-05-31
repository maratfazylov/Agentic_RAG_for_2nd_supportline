package com.hybridrag.bot;

import com.hybridrag.agent.AgentOrchestrator;
import com.hybridrag.routing.AnswerTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final TelegramClient telegramClient;
    private final AgentOrchestrator orchestrator;
    private final AnswerTracker answerTracker;

    public TelegramBot(TelegramClient telegramClient, AgentOrchestrator orchestrator,
                       AnswerTracker answerTracker) {
        this.telegramClient = telegramClient;
        this.orchestrator = orchestrator;
        this.answerTracker = answerTracker;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        var message = update.getMessage();
        var chatId = message.getChatId();
        var userId = message.getFrom().getId().toString();
        var text = message.getText();

        log.info("Received from {} (chat={}): {}", userId, chatId, text);

        // Показываем "печатает..."
        sendTyping(chatId);

        var username = message.getFrom().getUserName() != null
            ? message.getFrom().getUserName()
            : message.getFrom().getId().toString();

        if (message.getReplyToMessage() != null) {
            answerTracker.trackAnswer(
                chatId,
                message.getReplyToMessage().getMessageId(),
                username
            );
        }

        try {
            var response = orchestrator.handle(userId, chatId, text, username);
            sendMessage(chatId, response);
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendMessage(chatId, "Извините, произошла ошибка: " + e.getMessage());
        }
    }

    public void forwardToExpert(Long expertChatId, String originalQuestion, String fromUsername, String topic) {
        var text = "\uD83D\uDCCC New question about: " + topic + "\n"
            + "From: @" + fromUsername + "\n\n"
            + originalQuestion + "\n\n"
            + "\u2014 sent via KnowledgeBot";
        sendMessage(expertChatId, text);
    }

    private void sendMessage(Long chatId, String text) {
        try {
            var sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }

    private void sendTyping(Long chatId) {
        try {
            var sendAction = org.telegram.telegrambots.meta.api.methods.SendChatAction.builder()
                .chatId(chatId)
                .action("typing")
                .build();
            telegramClient.execute(sendAction);
        } catch (TelegramApiException e) {
            // non-critical
        }
    }
}

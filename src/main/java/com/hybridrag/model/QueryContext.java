package com.hybridrag.model;

import java.util.List;

public class QueryContext {
    private String query;
    private String userId;
    private String username;
    private Long chatId;
    private List<Document> retrievedDocuments;
    private List<ChatMessage> conversationHistory;
    private String systemPrompt;

    public QueryContext() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public List<Document> getRetrievedDocuments() { return retrievedDocuments; }
    public void setRetrievedDocuments(List<Document> retrievedDocuments) { this.retrievedDocuments = retrievedDocuments; }

    public List<ChatMessage> getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(List<ChatMessage> conversationHistory) { this.conversationHistory = conversationHistory; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}

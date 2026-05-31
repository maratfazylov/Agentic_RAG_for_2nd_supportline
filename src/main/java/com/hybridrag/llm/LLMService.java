package com.hybridrag.llm;

import com.hybridrag.model.ChatMessage;

import java.util.List;

public interface LLMService {

    String chat(List<ChatMessage> messages);

    String chat(List<ChatMessage> messages, String systemPrompt);

    List<Float> embed(String text);

    List<List<Float>> embedBatch(List<String> texts);
}

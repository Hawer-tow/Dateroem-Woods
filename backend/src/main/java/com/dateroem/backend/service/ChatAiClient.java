package com.dateroem.backend.service;

import java.util.List;

import com.dateroem.backend.dto.AiChatReply;
import com.dateroem.backend.dto.ChatTurn;

// "before i pay i want to ensure the chat bot is ok ... then i can
// proceed to the paying ones" - AiChatService talks to whichever
// implementation app.ai.chat-provider currently names (GroqClient's free
// tier for now, AnthropicClient once billing is funded) through this one
// shape, so switching providers later is a property change, not a code
// change.
public interface ChatAiClient {
    boolean isConfigured();

    AiChatReply chat(String systemPrompt, List<ChatTurn> history, String userMessage);
}

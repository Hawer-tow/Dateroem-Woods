package com.dateroem.backend.dto;

// One turn of the AI chat conversation ("user" or "assistant") - shared
// between every chat-capable provider (AnthropicClient, GroqClient, ...)
// so AiChatService can build history once and hand it to whichever
// provider app.ai.chat-provider currently points at.
public record ChatTurn(String role, String content) {}

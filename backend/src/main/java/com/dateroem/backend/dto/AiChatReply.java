package com.dateroem.backend.dto;

// "provide a shortcut to that section as an alternative" - suggestedRoute
// is one of the mobile app's own known route strings (validated against
// an allow-list in AiChatService before ever reaching here), letting the
// app render an actual tappable button next to the answer instead of the
// user having to read directions and navigate manually.
public record AiChatReply(String answer, String suggestedRoute, String suggestedLabel) {}

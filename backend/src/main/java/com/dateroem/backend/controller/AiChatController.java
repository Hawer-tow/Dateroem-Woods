package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AiChatReply;
import com.dateroem.backend.dto.ChatTurn;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.AiChatService;
import com.dateroem.backend.service.RoleService;

// "implement the chat bot right below that in the same section to allow
// it to answer questions still role based but still the normal question
// and answer" - role comes from a real DB lookup (roleNameOf), never
// trusted from the client, same reasoning as every other actor-header
// endpoint in this codebase.
//
// "the ai should work even if you dont have an account but if you are
// logged in it will provide dedicated answers based on your account" -
// X-Actor-User-Id is now optional. The mobile app (channel defaults to
// it) always sends it, so its behavior is unchanged; the website can omit
// it for an anonymous visitor, in which case AiChatService answers
// without a role, without conversation history, and without persisting
// anything (AI_CHAT_MESSAGES.User_ID is NOT NULL - there's nothing valid
// to write for a visitor with no account).
//
// A website request that names an account must also carry that account's
// own sign-in token: the account id alone unlocks that customer's orders
// and chat history, so trusting a bare header would let anyone read
// anyone's by guessing ids.
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;
    private final RoleService roleService;
    private final JwtService jwtService;

    public AiChatController(AiChatService aiChatService, RoleService roleService, JwtService jwtService) {
        this.aiChatService = aiChatService;
        this.roleService = roleService;
        this.jwtService = jwtService;
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(
        @RequestHeader(value = "X-Actor-User-Id", required = false) Integer actorUserId,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (actorUserId == null) {
            return List.of();
        }
        // The website always sends its token here; the mobile server
        // doesn't have one to send (it's only reachable locally).
        if (authHeader != null) {
            requireSignedInAs(actorUserId, authHeader);
        }
        return aiChatService.history(actorUserId);
    }

    @PostMapping
    public AiChatReply sendMessage(
        @RequestBody ChatMessageRequest body,
        @RequestHeader(value = "X-Actor-User-Id", required = false) Integer actorUserId,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!aiChatService.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The chat assistant isn't configured yet - app.ai.chat-provider's API key is blank"
            );
        }
        if (body.message() == null || body.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        if (actorUserId != null && ("web".equals(body.channel()) || authHeader != null)) {
            requireSignedInAs(actorUserId, authHeader);
        }
        String roleName = actorUserId == null ? null : roleService.roleNameOf(actorUserId);
        return aiChatService.sendMessage(
            actorUserId, roleName, body.message().trim(), body.channel(), body.siteInfo(), body.history(), body.context()
        );
    }

    // Whether the website should invite questions right now (its chat
    // button only nudges visitors when this is true) - set up, and not at
    // the AI provider's per-minute limit. Says nothing else.
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("available", aiChatService.isAvailable());
    }

    private void requireSignedInAs(int actorUserId, String authHeader) {
        jwtService.validate(authHeader)
            .filter(tokenUserId -> tokenUserId == actorUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to use your account here"));
    }

    // channel distinguishes "which app is this?" so the assistant only
    // ever suggests routes that actually exist there (mobile app screens
    // vs. website pages are completely different route sets) - defaults
    // to "mobile" so the existing Android client, which never sends this
    // field, keeps working unchanged. siteInfo (the website's public
    // contact details) and history (a guest's own recent turns, kept only
    // in their browser) are website-only extras - see AiChatService.
    // context says which page a website question came from - {page:
    // "product", productId}, {page: "cart", cart: [{productId, quantity}]}
    // or {page: "search", search} - and the backend looks the products up
    // itself (AiChatService.buildVisitorContext).
    public record ChatMessageRequest(
        String message, String channel, Map<String, String> siteInfo, List<ChatTurn> history,
        Map<String, Object> context
    ) {}
}

package com.dateroem.backend.service;

import java.time.Duration;

/**
 * Every chat model the provider offers is at its per-minute limit right now
 * ("remembering that there is a limit to the number of prompts per minute
 * from my ai provider"). AiChatService turns this into an honest "ask again
 * in about N seconds" reply instead of a server error - an error used to
 * drop the website onto the keyword bot's canned non-answer.
 */
public class AiBusyException extends RuntimeException {

    private final Duration retryAfter;

    public AiBusyException(Duration retryAfter) {
        super("Chat provider is rate-limited; retry after " + retryAfter.toSeconds() + "s");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}

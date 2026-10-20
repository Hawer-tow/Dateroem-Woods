-- "implement the chat bot ... i know the ai might require some new
-- tables of it self" - stores each user's ongoing conversation with the
-- in-house chat assistant (role-based Q&A + navigation shortcuts), so it
-- has real multi-turn context instead of answering each message cold,
-- and so a conversation survives closing/reopening the app.
CREATE TABLE AI_CHAT_MESSAGES (
    Ai_Chat_Message_ID INT AUTO_INCREMENT,
    User_ID INT NOT NULL,
    -- 'user' | 'assistant' - who said it, not a business role.
    Role VARCHAR(20) NOT NULL,
    Content TEXT NOT NULL,
    -- "provide a shortcut to that section as an alternative" - a known
    -- mobile route the app can navigate to directly when the assistant's
    -- answer points somewhere specific; null on plain Q&A turns and on
    -- every user-authored message.
    Suggested_Route VARCHAR(100),
    Suggested_Label VARCHAR(200),
    Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (Ai_Chat_Message_ID),
    FOREIGN KEY (User_ID) REFERENCES USERS(User_ID)
);

CREATE INDEX idx_ai_chat_messages_user_created ON AI_CHAT_MESSAGES (User_ID, Created_At);

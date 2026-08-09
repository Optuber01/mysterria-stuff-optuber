package it.pino.zelchat.api.module;

import it.pino.zelchat.api.message.ChatMessage;

public interface ChatModule {
    default void load() {}
    default void unload() {}
    default void reload() {}
    void handleChatMessage(ChatMessage chatMessage);
}

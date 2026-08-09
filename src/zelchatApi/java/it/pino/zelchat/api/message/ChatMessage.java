package it.pino.zelchat.api.message;

import it.pino.zelchat.api.message.state.MessageState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface ChatMessage {
    Player getBukkitPlayer();
    MessageState getState();
    Component getMessage();
    void setState(MessageState state);
}

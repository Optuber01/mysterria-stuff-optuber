package net.mysterria.stuff.features.chatcontrol;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class JoinQuitMessageListener implements Listener {

    private final JoinQuitMessageStore store;
    private final LegacyComponentSerializer legacy;
    private final MiniMessage miniMessage;

    public JoinQuitMessageListener(JoinQuitMessageStore store) {
        this.store = store;
        this.legacy = LegacyComponentSerializer.builder().character('&').build();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        // First join: player has never played on this server before
        if (!player.hasPlayedBefore()) {
            String msg = store.getFirstJoinMessage();
            if (msg != null) {
                event.joinMessage(parseMiniMessage(msg, username));
                return;
            }
        }

        // Player-specific custom message (legacy & codes)
        String custom = store.getJoinMessage(username);
        if (custom != null) {
            event.joinMessage(legacy.deserialize(custom.replace("{player}", username)));
            return;
        }

        // Server-wide default message
        String def = store.getDefaultJoinMessage();
        if (def != null) {
            event.joinMessage(parseMiniMessage(def, username));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String username = event.getPlayer().getName();

        // Player-specific custom message (legacy & codes)
        String custom = store.getQuitMessage(username);
        if (custom != null) {
            event.quitMessage(legacy.deserialize(custom.replace("{player}", username)));
            return;
        }

        // Server-wide default message
        String def = store.getDefaultQuitMessage();
        if (def != null) {
            event.quitMessage(parseMiniMessage(def, username));
        }
    }

    private Component parseMiniMessage(String template, String username) {
        return miniMessage.deserialize(template.replace("{player}", username));
    }
}

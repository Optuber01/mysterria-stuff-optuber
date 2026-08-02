package net.mysterria.stuff.features.joinmsg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class JoinMsgListener implements Listener {

    private final JoinMsgStore store;
    private final LegacyComponentSerializer legacy;
    private final MiniMessage miniMessage;

    public JoinMsgListener(JoinMsgStore store) {
        this.store = store;
        this.legacy = LegacyComponentSerializer.builder().character('&').build();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // First join: player has never played on this server before
        if (!player.hasPlayedBefore() && store.getFirstJoinMessage() != null) {
            event.joinMessage(parseMiniMessage(store.getFirstJoinMessage(), player.getName()));
            return;
        }

        String message = store.resolveJoinMessage(player);
        event.joinMessage(message != null ? legacy.deserialize(message) : null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String message = store.resolveQuitMessage(player);
        event.quitMessage(message != null ? legacy.deserialize(message) : null);
    }

    private Component parseMiniMessage(String template, String username) {
        return miniMessage.deserialize(template.replace("{player}", username));
    }
}

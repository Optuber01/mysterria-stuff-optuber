package net.mysterria.stuff.features.joinmsg;

import net.kyori.adventure.text.Component;
import net.mysterria.stuff.utils.AdventureUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class JoinMsgListener implements Listener {

    private final JoinMsgStore store;

    public JoinMsgListener(JoinMsgStore store) {
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // First join: player has never played on this server before
        if (!player.hasPlayedBefore() && store.getFirstJoinMessage() != null) {
            event.joinMessage(parseTemplate(store.getFirstJoinMessage(), player.getName()));
            return;
        }

        String message = store.resolveJoinMessage(player);
        event.joinMessage(message != null ? AdventureUtil.parseUniversal(message) : null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String message = store.resolveQuitMessage(player);
        event.quitMessage(message != null ? AdventureUtil.parseUniversal(message) : null);
    }

    private Component parseTemplate(String template, String username) {
        return AdventureUtil.parseUniversal(template.replace("{player}", username));
    }
}

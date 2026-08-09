package net.mysterria.stuff.features.chat;

import it.pino.zelchat.api.ZelChatAPI;
import it.pino.zelchat.api.message.ChatMessage;
import it.pino.zelchat.api.message.state.MessageState;
import it.pino.zelchat.api.module.ChatModule;
import it.pino.zelchat.api.module.ModuleManager;
import it.pino.zelchat.api.module.annotation.ChatModuleSettings;
import it.pino.zelchat.api.module.priority.ModulePriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@ChatModuleSettings(pluginOwner = "MysterriaStuff", priority = ModulePriority.HIGHEST)
public final class ZelChatAliasIntegration implements ChatAliasIntegration, ChatModule {

    private static final Pattern SAFE_ALIAS = Pattern.compile("[a-z0-9]+");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final MysterriaStuff plugin;
    private final ModuleManager moduleManager;
    private volatile Map<String, String> aliases = Map.of();
    private boolean registered;

    private ZelChatAliasIntegration(MysterriaStuff plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    public static ZelChatAliasIntegration register(MysterriaStuff plugin) {
        ModuleManager moduleManager = ZelChatAPI.get().getModuleManager();
        ZelChatAliasIntegration integration = new ZelChatAliasIntegration(plugin, moduleManager);
        integration.reload();
        moduleManager.register(plugin, integration);
        integration.registered = true;
        return integration;
    }

    @Override
    public void reload() {
        Map<String, String> reloaded = new LinkedHashMap<>();
        plugin.getConfigManager().getChatAliases().forEach((configuredAlias, configuredCommand) -> {
            String alias = configuredAlias.toLowerCase(Locale.ROOT).trim();
            String command = configuredCommand.trim();
            while (command.startsWith("/")) {
                command = command.substring(1).trim();
            }

            if (!SAFE_ALIAS.matcher(alias).matches() || command.isBlank()
                    || CONTROL_CHARACTER.matcher(command).find()) {
                PrettyLogger.warn("Ignoring unsafe chat alias configuration: !" + configuredAlias);
                return;
            }
            reloaded.put(alias, command);
        });
        aliases = Collections.unmodifiableMap(reloaded);
        PrettyLogger.info("Loaded " + aliases.size() + " ZelChat channel aliases");
    }

    @Override
    public void handleChatMessage(ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();
        if (state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED) {
            return;
        }

        String content = PLAIN_TEXT.serialize(chatMessage.getMessage());
        ParsedAlias parsed = parse(content);
        if (parsed == null) {
            return;
        }

        // Cancel inside ZelChat before handing the already-filtered payload to the canonical command.
        chatMessage.setState(MessageState.CANCELLED);
        Player player = chatMessage.getBukkitPlayer();
        String commandLine = parsed.command() + " " + parsed.payload();
        plugin.getServer().getScheduler().runTask(plugin, () -> dispatch(player, commandLine));
    }

    private ParsedAlias parse(String content) {
        if (content == null || content.length() < 4 || content.charAt(0) != '!') {
            return null;
        }

        int separator = firstWhitespace(content);
        if (separator < 2) {
            return null;
        }

        String alias = content.substring(1, separator).toLowerCase(Locale.ROOT);
        String command = aliases.get(alias);
        if (command == null) {
            return null;
        }

        String payload = content.substring(separator).strip();
        if (payload.isEmpty() || CONTROL_CHARACTER.matcher(payload).find()) {
            return null;
        }
        return new ParsedAlias(command, payload);
    }

    private int firstWhitespace(String content) {
        for (int index = 1; index < content.length(); index++) {
            if (Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private void dispatch(Player player, String commandLine) {
        if (!plugin.isEnabled() || player == null || !player.isOnline()) {
            return;
        }

        if (!player.performCommand(commandLine)) {
            player.sendMessage(Component.text("That chat channel is currently unavailable.", NamedTextColor.RED));
        }
    }

    @Override
    public void close() {
        if (!registered) {
            return;
        }
        moduleManager.unregister(plugin, this);
        registered = false;
    }

    private record ParsedAlias(String command, String payload) {
    }
}

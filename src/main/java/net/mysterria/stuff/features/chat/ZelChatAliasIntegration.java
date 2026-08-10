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
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Routes a deliberately small, fixed set of chat shortcuts. Configuration can rename
 * those routes, but never supplies a command, so chat text cannot become an arbitrary
 * command execution primitive.
 *
 * <p>Messages are identified through {@link ChatMessage#getRawMessage()}, as required by
 * the public API contract. The formatted {@link ChatMessage#getMessage()} is deliberately
 * never parsed or reconstructed.</p>
 */
@ChatModuleSettings(pluginOwner = "MysterriaStuff", priority = ModulePriority.HIGH)
public final class ZelChatAliasIntegration implements ChatAliasIntegration, ChatModule {

    private static final Pattern SAFE_ALIAS = Pattern.compile("[a-z0-9]+");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");
    private static final Set<String> RESERVED_ALIASES = Set.of("help", "channels");

    private final MysterriaStuff plugin;
    private final ModuleManager moduleManager;
    private volatile Map<String, ChatShortcut> aliases = defaultAliases();
    private volatile Map<ChatShortcut, List<String>> aliasesByRoute = defaultAliasesByRoute();
    private boolean registered;

    private ZelChatAliasIntegration(MysterriaStuff plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    public static ZelChatAliasIntegration register(MysterriaStuff plugin) {
        ModuleManager moduleManager = ZelChatAPI.get().getModuleManager();
        ZelChatAliasIntegration integration = new ZelChatAliasIntegration(plugin, moduleManager);
        integration.reload();
        try {
            moduleManager.register(plugin, integration);
            integration.registered = true;
        } catch (LinkageError | RuntimeException registrationFailure) {
            // register() may have called the module before failing. Make a best-effort
            // cleanup so a retry cannot leave two alias modules active.
            try {
                moduleManager.unregister(plugin, integration);
            } catch (LinkageError | RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
        return integration;
    }

    @Override
    public void reload() {
        ConfigurationSection configuredAliases = plugin.getConfigManager().getConfig()
                .getConfigurationSection("chat-aliases.aliases");
        Map<String, ChatShortcut> reloaded = new LinkedHashMap<>();
        Map<ChatShortcut, List<String>> reloadedByRoute = new EnumMap<>(ChatShortcut.class);

        for (ChatShortcut shortcut : ChatShortcut.values()) {
            List<String> configured = configuredAliases == null || !configuredAliases.contains(shortcut.routeId())
                    ? shortcut.defaultAliases()
                    : configuredAliases.getStringList(shortcut.routeId());
            List<String> acceptedAliases = new ArrayList<>();

            for (String configuredAlias : configured) {
                String alias = configuredAlias.toLowerCase(Locale.ROOT).trim();
                if (!SAFE_ALIAS.matcher(alias).matches()) {
                    PrettyLogger.warn("Ignoring invalid chat alias for " + shortcut.routeId() + ": " + configuredAlias);
                    continue;
                }
                if (RESERVED_ALIASES.contains(alias)) {
                    PrettyLogger.warn("Ignoring reserved chat alias !" + alias + " for " + shortcut.routeId());
                    continue;
                }
                ChatShortcut existing = reloaded.putIfAbsent(alias, shortcut);
                if (existing != null && existing != shortcut) {
                    PrettyLogger.warn("Ignoring duplicate chat alias !" + alias + " for " + shortcut.routeId()
                            + "; it is already assigned to " + existing.routeId());
                    continue;
                }
                if (existing == null) {
                    acceptedAliases.add(alias);
                }
            }
            reloadedByRoute.put(shortcut, List.copyOf(acceptedAliases));
        }

        aliases = Collections.unmodifiableMap(reloaded);
        aliasesByRoute = Collections.unmodifiableMap(reloadedByRoute);
        PrettyLogger.info("Loaded " + aliases.size() + " typed ZelChat chat shortcuts");
    }

    @Override
    public void handleChatMessage(ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();
        if (state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED) {
            return;
        }

        String content = chatMessage.getRawMessage();
        if (content.isEmpty()) {
            return;
        }

        if (content.strip().equalsIgnoreCase("!help") || content.strip().equalsIgnoreCase("!channels")) {
            cancelAndRun(chatMessage, this::sendHelp);
            return;
        }

        ParsedShortcut parsed = parse(content);
        if (parsed == null) {
            return;
        }

        // The public API identifies FILTERED as a message modified by ZelChat, but does
        // not expose a filtered raw payload. Dispatching the original payload would
        // bypass that modification, so fail closed for recognized provider aliases.
        if (state == MessageState.FILTERED) {
            cancelAndRun(chatMessage, MessageState.FILTERED_CANCELLED, player -> player.sendMessage(Component.text(
                    "That shortcut was not sent because ZelChat filtered the message.", NamedTextColor.RED)));
            return;
        }

        if (!parsed.hasValidPayload()) {
            cancelAndRun(chatMessage, player -> player.sendMessage(Component.text(
                    "That chat shortcut requires a valid message. Use !help for usage.", NamedTextColor.RED)));
            return;
        }

        cancelAndRun(chatMessage, sender -> dispatchProviderCommand(sender, parsed));
    }

    private ParsedShortcut parse(String content) {
        if (content.length() < 2 || content.charAt(0) != '!') {
            return null;
        }

        int separator = firstWhitespace(content);
        int aliasEnd = separator < 0 ? content.length() : separator;

        ChatShortcut shortcut = aliases.get(content.substring(1, aliasEnd).toLowerCase(Locale.ROOT));
        if (shortcut == null) {
            return null;
        }

        int payloadStart = aliasEnd;
        while (payloadStart < content.length() && Character.isWhitespace(content.charAt(payloadStart))) {
            payloadStart++;
        }
        String payload = content.substring(payloadStart).strip();
        boolean validPayload = separator >= 0
                && !payload.isEmpty()
                && !CONTROL_CHARACTER.matcher(content).find();
        return new ParsedShortcut(shortcut, payload, validPayload);
    }

    private int firstWhitespace(String content) {
        for (int index = 1; index < content.length(); index++) {
            if (Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private void cancelAndRun(ChatMessage chatMessage, java.util.function.Consumer<Player> action) {
        cancelAndRun(chatMessage, MessageState.CANCELLED, action);
    }

    private void cancelAndRun(ChatMessage chatMessage, MessageState cancelledState,
                              java.util.function.Consumer<Player> action) {
        chatMessage.setState(cancelledState);
        Player player = chatMessage.getBukkitPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }

    private void dispatchProviderCommand(Player player, ParsedShortcut parsed) {
        String command = parsed.shortcut().providerCommand();
        try {
            if (player.performCommand(command + " " + parsed.payload())) {
                return;
            }
        } catch (RuntimeException dispatchFailure) {
            PrettyLogger.error("Failed to dispatch chat shortcut " + parsed.shortcut().routeId()
                    + ": " + dispatchFailure.getMessage());
        }
        if (player.isOnline()) {
            player.sendMessage(Component.text("That chat channel is currently unavailable.", NamedTextColor.RED));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("Chat shortcuts", NamedTextColor.GOLD));
        player.sendMessage(Component.text("!<message> — global chat (native)", NamedTextColor.GRAY));
        sendHelpLine(player, ChatShortcut.CHURCH, "<message> — church chat");
        sendHelpLine(player, ChatShortcut.ORGANIZATION, "<message> — organization chat");
        sendHelpLine(player, ChatShortcut.LANDS, "<message> — Lands chat");
        sendHelpLine(player, ChatShortcut.NATIONS, "<message> — Nations chat");
        sendHelpLine(player, ChatShortcut.PARTY, "<message> — dungeon party chat");
        sendHelpLine(player, ChatShortcut.STAFF, "<message> — staff chat");
        sendHelpLine(player, ChatShortcut.PRIVATE_MESSAGE, "<player> <message> — private message");
        sendHelpLine(player, ChatShortcut.REPLY, "<message> — reply to the last private message");
        player.sendMessage(Component.text("Shortcuts require a message; use the provider's normal command for chat toggles.", NamedTextColor.DARK_GRAY));
    }

    private void sendHelpLine(Player player, ChatShortcut shortcut, String usage) {
        List<String> routeAliases = aliasesByRoute.get(shortcut);
        if (routeAliases == null || routeAliases.isEmpty()) {
            return;
        }
        player.sendMessage(Component.text("!" + routeAliases.getFirst() + " " + usage, NamedTextColor.GRAY));
    }

    @Override
    public void close() {
        if (!registered) {
            return;
        }
        try {
            moduleManager.unregister(plugin, this);
        } finally {
            registered = false;
        }
    }

    private static Map<String, ChatShortcut> defaultAliases() {
        Map<String, ChatShortcut> defaults = new LinkedHashMap<>();
        for (ChatShortcut shortcut : ChatShortcut.values()) {
            for (String alias : shortcut.defaultAliases()) {
                defaults.put(alias, shortcut);
            }
        }
        return Collections.unmodifiableMap(defaults);
    }

    private static Map<ChatShortcut, List<String>> defaultAliasesByRoute() {
        Map<ChatShortcut, List<String>> defaults = new EnumMap<>(ChatShortcut.class);
        for (ChatShortcut shortcut : ChatShortcut.values()) {
            defaults.put(shortcut, shortcut.defaultAliases());
        }
        return Collections.unmodifiableMap(defaults);
    }

    private enum ChatShortcut {
        CHURCH("church", "cc", "c", "cc", "church"),
        ORGANIZATION("organization", "oc", "o", "oc", "org", "order"),
        LANDS("lands", "lands chat", "l", "land", "lands"),
        NATIONS("nations", "nations chat", "n", "nation", "nations"),
        PARTY("party", "party chat", "p", "party", "dp", "dparty"),
        STAFF("staff", "staffchat", "s", "sc", "staff"),
        // ZelChat's public configuration documents these command names. Delegating to
        // the commands retains ZelChat's own privacy, ignore and reply checks.
        PRIVATE_MESSAGE("message", "msg", "m", "msg", "w", "whisper", "tell"),
        REPLY("reply", "reply", "r", "reply");

        private final String routeId;
        private final String providerCommand;
        private final List<String> defaultAliases;

        ChatShortcut(String routeId, String providerCommand, String... defaultAliases) {
            this.routeId = routeId;
            this.providerCommand = providerCommand;
            this.defaultAliases = List.of(defaultAliases);
        }

        String routeId() {
            return routeId;
        }

        String providerCommand() {
            return providerCommand;
        }

        List<String> defaultAliases() {
            return defaultAliases;
        }
    }

    private record ParsedShortcut(ChatShortcut shortcut, String payload, boolean hasValidPayload) {
    }
}

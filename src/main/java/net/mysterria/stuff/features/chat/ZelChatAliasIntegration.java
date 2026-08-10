package net.mysterria.stuff.features.chat;

import it.pino.zelchat.api.ZelChatAPI;
import it.pino.zelchat.api.message.ChatMessage;
import it.pino.zelchat.api.message.state.MessageState;
import it.pino.zelchat.api.module.ChatModule;
import it.pino.zelchat.api.module.ModuleManager;
import it.pino.zelchat.api.module.annotation.ChatModuleSettings;
import it.pino.zelchat.api.module.priority.ModulePriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * <p>Staff chat deliberately has no shortcut. The deployed ZelChat implementation can
 * treat a staff-prefixed message as public when its own permission or configuration check
 * fails, so players must use ZelChat's native {@code #} and {@code /staffchat} paths.</p>
 */
@ChatModuleSettings(pluginOwner = "MysterriaStuff", priority = ModulePriority.HIGHEST)
public final class ZelChatAliasIntegration implements ChatAliasIntegration, ChatModule {

    private static final Pattern SAFE_ALIAS = Pattern.compile("[a-z0-9]+");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");
    private static final Set<String> RESERVED_ALIASES = Set.of("help", "channels");
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final TextReplacementConfig REMOVE_FIRST_BANG = TextReplacementConfig.builder()
            .match(Pattern.compile("^!"))
            .replacement(Component.empty())
            .build();

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
        moduleManager.register(plugin, integration);
        integration.registered = true;
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
        if (isCancelled(chatMessage)) {
            return;
        }

        String content = PLAIN_TEXT.serialize(chatMessage.getMessage());
        if (content == null || content.isEmpty() || CONTROL_CHARACTER.matcher(content).find()) {
            return;
        }

        // Keep this chat message in ZelChat's existing delivery pipeline. Re-entering it
        // asynchronously would make same-text messages impossible to correlate safely.
        if (content.startsWith("!!")) {
            chatMessage.setMessage(chatMessage.getMessage().replaceText(REMOVE_FIRST_BANG));
            return;
        }

        if (content.equalsIgnoreCase("!help") || content.equalsIgnoreCase("!channels")) {
            cancelAndRun(chatMessage, this::sendHelp);
            return;
        }

        ParsedShortcut parsed = parse(content);
        if (parsed == null) {
            return;
        }

        if (parsed.shortcut() == ChatShortcut.GLOBAL) {
            chatMessage.setMessage(removeGlobalShortcut(chatMessage.getMessage(), parsed.prefix()));
            return;
        }
        cancelAndRun(chatMessage, sender -> dispatchProviderCommand(sender, parsed));
    }

    private boolean isCancelled(ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();
        return state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED;
    }

    private ParsedShortcut parse(String content) {
        if (content.length() < 4 || content.charAt(0) != '!') {
            return null;
        }

        int separator = firstWhitespace(content);
        if (separator < 2) {
            return null;
        }

        ChatShortcut shortcut = aliases.get(content.substring(1, separator).toLowerCase(Locale.ROOT));
        if (shortcut == null) {
            return null;
        }

        int payloadStart = separator;
        while (payloadStart < content.length() && Character.isWhitespace(content.charAt(payloadStart))) {
            payloadStart++;
        }
        String payload = content.substring(payloadStart).strip();
        if (payload.isEmpty() || CONTROL_CHARACTER.matcher(payload).find()) {
            return null;
        }
        return new ParsedShortcut(shortcut, content.substring(0, payloadStart), payload);
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
        chatMessage.setState(MessageState.CANCELLED);
        Player player = chatMessage.getBukkitPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }

    private void dispatchProviderCommand(Player player, ParsedShortcut parsed) {
        String command = parsed.shortcut().providerCommand();
        if (!player.performCommand(command + " " + parsed.payload())) {
            player.sendMessage(Component.text("That chat channel is currently unavailable.", NamedTextColor.RED));
        }
    }

    /**
     * Strip the consumed global prefix only. Adventure retains the styling and events on
     * every component after that prefix instead of recreating the chat body as plain text.
     */
    private Component removeGlobalShortcut(Component message, String prefix) {
        TextReplacementConfig replacement = TextReplacementConfig.builder()
                .match(Pattern.compile("^" + Pattern.quote(prefix)))
                .replacement(Component.empty())
                .build();
        return message.replaceText(replacement);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("Chat shortcuts", NamedTextColor.GOLD));
        sendHelpLine(player, ChatShortcut.GLOBAL, "<message> — global chat");
        sendHelpLine(player, ChatShortcut.CHURCH, "<message> — church chat");
        sendHelpLine(player, ChatShortcut.ORGANIZATION, "<message> — organization chat");
        sendHelpLine(player, ChatShortcut.LANDS, "<message> — Lands chat");
        sendHelpLine(player, ChatShortcut.NATIONS, "<message> — Nations chat");
        sendHelpLine(player, ChatShortcut.PARTY, "<message> — dungeon party chat");
        sendHelpLine(player, ChatShortcut.PRIVATE_MESSAGE, "<player> <message> — private message");
        sendHelpLine(player, ChatShortcut.REPLY, "<message> — reply to the last private message");
        player.sendMessage(Component.text("Use !! to send a literal ! message globally. Shortcuts require a message; use the provider's normal command for chat toggles. ZelChat staff chat remains native: #<message> or /staffchat when enabled.", NamedTextColor.DARK_GRAY));
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
        moduleManager.unregister(plugin, this);
        registered = false;
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
        GLOBAL("global", null, "g", "global"),
        CHURCH("church", "cc", "c", "cc", "church"),
        ORGANIZATION("organization", "oc", "o", "oc", "org", "order"),
        LANDS("lands", "lands chat", "l", "land", "lands"),
        NATIONS("nations", "nations chat", "n", "nation", "nations"),
        PARTY("party", "p", "p", "party", "dp", "dparty"),
        // These bare commands are the exact commands registered by deployed ZelChat.
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

    private record ParsedShortcut(ChatShortcut shortcut, String prefix, String payload) {
    }
}

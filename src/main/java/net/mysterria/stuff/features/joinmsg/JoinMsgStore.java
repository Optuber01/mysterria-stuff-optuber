package net.mysterria.stuff.features.joinmsg;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores custom join/quit messages keyed primarily by player UUID (immune to
 * renames, Bedrock/Floodgate name prefixes, and any other nickname quirks).
 * The last-known name is kept alongside each entry purely for admin
 * readability and command lookups.
 * <p>
 * Entries that only have a name (imported from the legacy ChatControl-style
 * .rs format, or created by an admin for a player who has never joined) live
 * in a "pending" bucket and are automatically promoted to a UUID entry the
 * next time a matching player is seen online.
 */
public class JoinMsgStore {

    public enum SetResult {
        OK,
        MISSING_PLACEHOLDER_JOIN,
        MISSING_PLACEHOLDER_QUIT,
        WRITE_ERROR
    }

    public static class MessageEntry {
        public final UUID uuid;
        public String name;
        public String join;
        public String quit;

        MessageEntry(UUID uuid, String name, String join, String quit) {
            this.uuid = uuid;
            this.name = name;
            this.join = join;
            this.quit = quit;
        }
    }

    private final MysterriaStuff plugin;

    private final Map<UUID, MessageEntry> byUuid = new HashMap<>();
    private final Map<String, MessageEntry> pending = new HashMap<>();

    private String defaultJoinMessage;
    private String defaultQuitMessage;
    private String firstJoinMessage;

    public JoinMsgStore(MysterriaStuff plugin) {
        this.plugin = plugin;
        load();
    }

    // ---------------------------------------------------------------
    // Loading / saving
    // ---------------------------------------------------------------

    public void load() {
        byUuid.clear();
        pending.clear();
        defaultJoinMessage = null;
        defaultQuitMessage = null;
        firstJoinMessage = null;

        File file = getStoreFile();
        if (!file.exists()) {
            if (!migrateLegacyFormat()) {
                PrettyLogger.info("No join/quit message store found, starting fresh");
                return;
            }
        } else {
            readFrom(YamlConfiguration.loadConfiguration(file));
        }

        PrettyLogger.info("Loaded " + byUuid.size() + " player join/quit message(s)"
                + (pending.isEmpty() ? "" : ", " + pending.size() + " pending name match(es)")
                + (defaultJoinMessage != null || defaultQuitMessage != null ? ", default message(s)" : "")
                + (firstJoinMessage != null ? ", first-join message" : ""));
    }

    private void readFrom(YamlConfiguration yaml) {
        defaultJoinMessage = yaml.getString("default.join", null);
        defaultQuitMessage = yaml.getString("default.quit", null);
        firstJoinMessage = yaml.getString("first-join", null);

        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection s = playersSection.getConfigurationSection(key);
                    if (s == null) continue;
                    byUuid.put(uuid, new MessageEntry(uuid, s.getString("name", key), s.getString("join"), s.getString("quit")));
                } catch (IllegalArgumentException e) {
                    PrettyLogger.warn("Skipping invalid UUID key in join/quit store: " + key);
                }
            }
        }

        ConfigurationSection pendingSection = yaml.getConfigurationSection("pending");
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                ConfigurationSection s = pendingSection.getConfigurationSection(key);
                if (s == null) continue;
                pending.put(key.toLowerCase(), new MessageEntry(null, s.getString("name", key), s.getString("join"), s.getString("quit")));
            }
        }
    }

    public boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();

        if (defaultJoinMessage != null) yaml.set("default.join", defaultJoinMessage);
        if (defaultQuitMessage != null) yaml.set("default.quit", defaultQuitMessage);
        if (firstJoinMessage != null) yaml.set("first-join", firstJoinMessage);

        for (MessageEntry entry : byUuid.values()) {
            String base = "players." + entry.uuid;
            yaml.set(base + ".name", entry.name);
            if (entry.join != null) yaml.set(base + ".join", entry.join);
            if (entry.quit != null) yaml.set(base + ".quit", entry.quit);
        }

        for (Map.Entry<String, MessageEntry> e : pending.entrySet()) {
            String base = "pending." + e.getKey();
            MessageEntry entry = e.getValue();
            yaml.set(base + ".name", entry.name);
            if (entry.join != null) yaml.set(base + ".join", entry.join);
            if (entry.quit != null) yaml.set(base + ".quit", entry.quit);
        }

        try {
            File file = getStoreFile();
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException e) {
            PrettyLogger.warn("Failed to save join/quit message store: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------
    // One-time legacy .rs migration
    // ---------------------------------------------------------------

    private boolean migrateLegacyFormat() {
        File dir = getMessagesDir();
        File joinFile = new File(dir, "join.rs");
        File quitFile = new File(dir, "quit.rs");

        if (!joinFile.exists() && !quitFile.exists()) {
            return false;
        }

        PrettyLogger.info("Migrating legacy ChatControl join/quit format to the new store...");

        Map<String, String> legacyJoin = new HashMap<>();
        Map<String, String> legacyQuit = new HashMap<>();
        String[] legacyDefaultJoin = new String[1];
        String[] legacyDefaultQuit = new String[1];
        String[] legacyFirstJoin = new String[1];

        if (joinFile.exists()) parseLegacyFile(joinFile, legacyJoin, "join", legacyDefaultJoin, legacyFirstJoin);
        if (quitFile.exists()) parseLegacyFile(quitFile, legacyQuit, "quit", legacyDefaultQuit, null);

        defaultJoinMessage = legacyDefaultJoin[0];
        defaultQuitMessage = legacyDefaultQuit[0];
        firstJoinMessage = legacyFirstJoin[0];

        Set<String> names = new HashSet<>();
        names.addAll(legacyJoin.keySet());
        names.addAll(legacyQuit.keySet());

        for (String name : names) {
            pending.put(name.toLowerCase(), new MessageEntry(null, name, legacyJoin.get(name), legacyQuit.get(name)));
        }

        save();

        backupLegacyFile(joinFile);
        backupLegacyFile(quitFile);

        PrettyLogger.success("Migrated " + names.size() + " legacy join/quit message(s). "
                + "Each will attach to its player's UUID the next time that player is seen online (name match). "
                + "Old .rs files were renamed with a .migrated suffix.");
        return true;
    }

    private void backupLegacyFile(File file) {
        if (!file.exists()) return;
        File backup = new File(file.getParentFile(), file.getName() + ".migrated");
        if (!file.renameTo(backup)) {
            PrettyLogger.warn("Could not rename legacy file " + file.getName() + " after migration");
        }
    }

    private void parseLegacyFile(File file, Map<String, String> out, String type, String[] defaultOut, String[] firstJoinOut) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            String currentGroup = null;
            boolean inMessageBlock = false;
            String suffix = "-" + type + "-message";

            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("group ")) {
                    String groupName = line.substring("group ".length());
                    if (groupName.equals("default")) {
                        currentGroup = "default";
                    } else if (firstJoinOut != null && groupName.equals("firstjoinmessage")) {
                        currentGroup = "firstjoinmessage";
                    } else if (groupName.endsWith(suffix)) {
                        currentGroup = groupName.substring(0, groupName.length() - suffix.length());
                    } else {
                        currentGroup = null;
                    }
                    inMessageBlock = false;
                } else if (line.equals("message:") && currentGroup != null) {
                    inMessageBlock = true;
                } else if (inMessageBlock && line.startsWith("- ") && currentGroup != null) {
                    String message = line.substring(2);
                    if (currentGroup.equals("default")) {
                        defaultOut[0] = message;
                    } else if (firstJoinOut != null && currentGroup.equals("firstjoinmessage")) {
                        firstJoinOut[0] = message;
                    } else {
                        out.put(currentGroup, message);
                    }
                    inMessageBlock = false;
                }
            }
        } catch (IOException e) {
            PrettyLogger.warn("Failed to parse legacy " + type + " messages from " + file.getName() + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Runtime resolution (used by the join/quit listener)
    // ---------------------------------------------------------------

    public String resolveJoinMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        if (entry != null && entry.join != null && !entry.join.isEmpty()) {
            return entry.join.replace("{player}", player.getName());
        }
        if (defaultJoinMessage != null && !defaultJoinMessage.isEmpty()) {
            return defaultJoinMessage.replace("{player}", player.getName());
        }
        return null;
    }

    public String resolveQuitMessage(Player player) {
        MessageEntry entry = resolveEntry(player);
        if (entry != null && entry.quit != null && !entry.quit.isEmpty()) {
            return entry.quit.replace("{player}", player.getName());
        }
        if (defaultQuitMessage != null && !defaultQuitMessage.isEmpty()) {
            return defaultQuitMessage.replace("{player}", player.getName());
        }
        return null;
    }

    public String getFirstJoinMessage() {
        return firstJoinMessage;
    }

    /**
     * Looks up this player's UUID record, self-healing any pending
     * name-matched legacy entry into it along the way.
     */
    private MessageEntry resolveEntry(Player player) {
        UUID uuid = player.getUniqueId();
        MessageEntry entry = byUuid.get(uuid);

        MessageEntry legacyMatch = pending.remove(sanitizeKey(player.getName()));
        if (legacyMatch != null) {
            if (entry == null) {
                entry = new MessageEntry(uuid, player.getName(), legacyMatch.join, legacyMatch.quit);
                byUuid.put(uuid, entry);
            }
            save();
        } else if (entry != null && !player.getName().equals(entry.name)) {
            entry.name = player.getName();
            save();
        }

        return entry;
    }

    // ---------------------------------------------------------------
    // Admin / self-service mutation API
    // ---------------------------------------------------------------

    public SetResult setPlayerMessages(OfflinePlayer target, String joinMessage, String quitMessage) {
        if (joinMessage != null && !joinMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_JOIN;
        }
        if (quitMessage != null && !quitMessage.contains("%player%")) {
            return SetResult.MISSING_PLACEHOLDER_QUIT;
        }

        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        UUID uuid = target.getUniqueId();

        pending.remove(sanitizeKey(name));

        MessageEntry entry = byUuid.computeIfAbsent(uuid, id -> new MessageEntry(id, name, null, null));
        entry.name = name;
        if (joinMessage != null) entry.join = sanitize(joinMessage).replace("%player%", "{player}");
        if (quitMessage != null) entry.quit = sanitize(quitMessage).replace("%player%", "{player}");

        return save() ? SetResult.OK : SetResult.WRITE_ERROR;
    }

    public boolean removePlayerMessages(OfflinePlayer target, boolean removeJoin, boolean removeQuit) {
        boolean changed = false;

        MessageEntry entry = byUuid.get(target.getUniqueId());
        if (entry != null) {
            if (removeJoin) { entry.join = null; changed = true; }
            if (removeQuit) { entry.quit = null; changed = true; }
            if (entry.join == null && entry.quit == null) {
                byUuid.remove(target.getUniqueId());
            }
        }

        String name = target.getName();
        MessageEntry pend = name != null ? pending.get(sanitizeKey(name)) : null;
        if (pend != null) {
            if (removeJoin) { pend.join = null; changed = true; }
            if (removeQuit) { pend.quit = null; changed = true; }
            if (pend.join == null && pend.quit == null) {
                pending.remove(sanitizeKey(name));
            }
        }

        if (changed) save();
        return changed;
    }

    public MessageEntry getEntry(OfflinePlayer target) {
        MessageEntry entry = byUuid.get(target.getUniqueId());
        if (entry != null) return entry;
        String name = target.getName();
        return name != null ? pending.get(sanitizeKey(name)) : null;
    }

    public List<MessageEntry> listEntries() {
        List<MessageEntry> all = new ArrayList<>(byUuid.values());
        all.addAll(pending.values());
        return all;
    }

    public String getDefaultJoinMessage() {
        return defaultJoinMessage;
    }

    public String getDefaultQuitMessage() {
        return defaultQuitMessage;
    }

    public boolean setDefaultJoinMessage(String message) {
        this.defaultJoinMessage = message;
        return save();
    }

    public boolean setDefaultQuitMessage(String message) {
        this.defaultQuitMessage = message;
        return save();
    }

    public boolean setFirstJoinMessage(String message) {
        this.firstJoinMessage = message;
        return save();
    }

    /**
     * Resolves a command argument (exact UUID, currently online name, a
     * previously-seen name, or a locally cached offline name) to a player
     * reference. Never performs a blocking Mojang lookup.
     */
    public OfflinePlayer resolveTarget(String nameOrUuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException ignored) {
            // not a UUID, fall through to name-based resolution
        }

        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online != null) return online;

        for (MessageEntry entry : byUuid.values()) {
            if (entry.name.equalsIgnoreCase(nameOrUuid)) {
                return Bukkit.getOfflinePlayer(entry.uuid);
            }
        }

        return Bukkit.getOfflinePlayerIfCached(nameOrUuid);
    }

    private String sanitize(String message) {
        return message.replace("\0", "").replace("\r", "").replace("\n", " ")
                .replace("`", "").replace("$(", "").replace("${", "");
    }

    private String sanitizeKey(String name) {
        return name == null ? "" : name.toLowerCase();
    }

    private File getMessagesDir() {
        return new File(plugin.getDataFolder(), "messages");
    }

    private File getStoreFile() {
        return new File(getMessagesDir(), "join-quit-messages.yml");
    }
}

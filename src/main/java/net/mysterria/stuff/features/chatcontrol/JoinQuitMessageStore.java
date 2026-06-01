package net.mysterria.stuff.features.chatcontrol;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class JoinQuitMessageStore {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");

    private static final String GROUP_DEFAULT = "default";
    private static final String GROUP_FIRST_JOIN = "firstjoinmessage";

    private final MysterriaStuff plugin;
    private final Map<String, String> joinMessages = new HashMap<>();
    private final Map<String, String> quitMessages = new HashMap<>();
    private String defaultJoinMessage = null;
    private String defaultQuitMessage = null;
    private String firstJoinMessage = null;

    public JoinQuitMessageStore(MysterriaStuff plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        joinMessages.clear();
        quitMessages.clear();
        defaultJoinMessage = null;
        defaultQuitMessage = null;
        firstJoinMessage = null;

        File dir = getMessagesDir();
        if (!dir.exists()) dir.mkdirs();

        File joinFile = new File(dir, "join.rs");
        File quitFile = new File(dir, "quit.rs");

        if (joinFile.exists()) parseFile(joinFile, joinMessages, "join");
        if (quitFile.exists()) parseFile(quitFile, quitMessages, "quit");

        PrettyLogger.info("Loaded " + joinMessages.size() + " join and " + quitMessages.size() + " quit messages"
                + (defaultJoinMessage != null ? ", default join" : "")
                + (firstJoinMessage != null ? ", first-join" : ""));
    }

    private void parseFile(File file, Map<String, String> map, String type) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            String currentGroup = null;
            boolean inMessageBlock = false;
            String suffix = "-" + type + "-message";

            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("group ")) {
                    String groupName = line.substring("group ".length());
                    if (groupName.equals(GROUP_DEFAULT) || groupName.equals(GROUP_FIRST_JOIN)) {
                        currentGroup = groupName;
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
                    switch (currentGroup) {
                        case GROUP_DEFAULT -> {
                            if (type.equals("join")) defaultJoinMessage = message;
                            else defaultQuitMessage = message;
                        }
                        case GROUP_FIRST_JOIN -> firstJoinMessage = message;
                        default -> map.put(currentGroup.toLowerCase(), message);
                    }
                    inMessageBlock = false;
                }
            }
        } catch (IOException e) {
            PrettyLogger.warn("Failed to parse " + type + " messages from " + file.getName() + ": " + e.getMessage());
        }
    }

    public boolean writePlayerMessages(Player player, String joinMessage, String quitMessage) {
        String username = player.getName();

        if (!VALID_USERNAME.matcher(username).matches()) {
            PrettyLogger.warn("Invalid username format: " + username);
            return false;
        }

        if (!joinMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("join-missing-placeholder"));
            return false;
        }

        if (!quitMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("quit-missing-placeholder"));
            return false;
        }

        String sanitizedJoin = sanitize(joinMessage).replace("%player%", "{player}");
        String sanitizedQuit = sanitize(quitMessage).replace("%player%", "{player}");

        try {
            File dir = getMessagesDir();
            if (!dir.exists()) dir.mkdirs();

            if (!writeOrUpdate(new File(dir, "join.rs"), username, sanitizedJoin, "join")) return false;
            if (!writeOrUpdate(new File(dir, "quit.rs"), username, sanitizedQuit, "quit")) return false;

            joinMessages.put(username.toLowerCase(), sanitizedJoin);
            quitMessages.put(username.toLowerCase(), sanitizedQuit);

            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("success"));
            PrettyLogger.info("Set custom messages for " + username);
            return true;
        } catch (Exception e) {
            PrettyLogger.warn("Error writing messages for " + username + ": " + e.getMessage());
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("write-error"));
            return false;
        }
    }

    public String getJoinMessage(String username) {
        return joinMessages.get(username.toLowerCase());
    }

    public String getQuitMessage(String username) {
        return quitMessages.get(username.toLowerCase());
    }

    public String getDefaultJoinMessage() {
        return defaultJoinMessage;
    }

    public String getDefaultQuitMessage() {
        return defaultQuitMessage;
    }

    public String getFirstJoinMessage() {
        return firstJoinMessage;
    }

    private String sanitize(String message) {
        return message.replace("\0", "").replace("\r", "").replace("\n", " ")
                .replace("`", "").replace("$(", "").replace("${", "");
    }

    private boolean writeOrUpdate(File file, String username, String message, String type) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            List<String> lines = file.length() > 0 ? Files.readAllLines(file.toPath()) : new ArrayList<>();
            String groupMarker = "group " + username + "-" + type + "-message";
            lines = removeSection(lines, groupMarker);

            int insertAt = findInsertPoint(lines);
            List<String> section = new ArrayList<>();
            if (insertAt > 0 && insertAt < lines.size() && !lines.get(insertAt - 1).isEmpty()) {
                section.add("");
            }
            section.add(groupMarker);
            section.add("require sender script \"{player}\" == \"" + username + "\"");
            section.add("message:");
            section.add("- " + message);
            section.add("");
            lines.addAll(insertAt, section);

            Files.write(file.toPath(), lines);
            PrettyLogger.debug("Wrote " + type + " message for " + username);
            return true;
        } catch (IOException e) {
            PrettyLogger.warn("Failed to write to " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private List<String> removeSection(List<String> lines, String groupMarker) {
        List<String> result = new ArrayList<>();
        boolean inTarget = false;
        boolean found = false;

        for (String s : lines) {
            String line = s.trim();
            if (line.equals(groupMarker)) { inTarget = true; found = true; continue; }
            if (inTarget && line.startsWith("group ")) inTarget = false;
            if (!inTarget) result.add(s);
        }

        if (found) {
            while (!result.isEmpty() && result.getLast().trim().isEmpty()) result.removeLast();
        }

        return result;
    }

    private int findInsertPoint(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty() && !line.startsWith("#")) return i;
        }
        return lines.size();
    }

    private File getMessagesDir() {
        return new File(plugin.getDataFolder(), "messages");
    }
}

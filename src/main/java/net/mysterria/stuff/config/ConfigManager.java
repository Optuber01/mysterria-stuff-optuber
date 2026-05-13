package net.mysterria.stuff.config;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.features.zones.CoiZone;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class ConfigManager {

    private final MysterriaStuff plugin;
    private FileConfiguration config;

    public ConfigManager(MysterriaStuff plugin) {
        this.plugin = plugin;
        loadConfig();
    }


    public void loadConfig() {

        plugin.saveDefaultConfig();


        plugin.reloadConfig();
        config = plugin.getConfig();

        PrettyLogger.debug("Configuration loaded");
    }


    public void reloadConfig() {
        loadConfig();
        PrettyLogger.info("Configuration reloaded");
    }


    public void saveConfig() {
        plugin.saveConfig();
        PrettyLogger.debug("Configuration saved");
    }


    public boolean isDebugMode() {
        return config.getBoolean("debug-mode", false);
    }

    public void setDebugMode(boolean enabled) {
        config.set("debug-mode", enabled);
        saveConfig();
    }

    public String getPrefixColor() {
        return config.getString("prefix-color", "#AA55FF");
    }


    public boolean isElytraBlockerEnabled() {
        return config.getBoolean("features.elytra-blocker", true);
    }

    public boolean isLightningFixEnabled() {
        return config.getBoolean("features.lightning-fix", true);
    }

    public boolean isCoiProtectionEnabled() {
        return config.getBoolean("features.coi-protection", true);
    }

    public boolean isBoosterPatriarchEnabled() {
        return config.getBoolean("features.booster-patriarch", true);
    }

    public boolean isRecipeManagerEnabled() {
        return config.getBoolean("features.recipe-manager", true);
    }

    public boolean isUniversalTokenEnabled() {
        return config.getBoolean("features.universal-token", true);
    }

    public boolean isChatControlTokenEnabled() {
        return config.getBoolean("features.chatcontrol-token", true);
    }

    public boolean isLastSprintEnabled() {
        return config.getBoolean("features.last-sprint", true);
    }

    public boolean isDungeonWorldEnforcerEnabled() {
        return config.getBoolean("features.dungeon-world-enforcer", true);
    }

    public String getDungeonWorldName() {
        return config.getString("dungeon-world-enforcer.world-name", "dungeons_normal_0");
    }

    public boolean isLastSprintActive() {
        return config.getBoolean("last-sprint.active", false);
    }

    public void setLastSprintActive(boolean active) {
        config.set("last-sprint.active", active);
        saveConfig();
    }



    public boolean isResetAttributesOnJoin() {
        return config.getBoolean("coi-protection.reset-attributes-on-join", true);
    }

    public boolean isRestrictSpectatorNoclip() {
        return config.getBoolean("coi-protection.restrict-spectator-noclip", true);
    }

    public boolean isBlockNightmarePickups() {
        return config.getBoolean("coi-protection.block-nightmare-pickups", true);
    }

    public boolean isNightmareKeepInventory() {
        return config.getBoolean("coi-protection.nightmare-keep-inventory", true);
    }

    public boolean isCoiZonesEnabled() {
        return config.getBoolean("coi-zones.enabled", false);
    }

    public List<CoiZone> getCoiZones() {
        List<CoiZone> result = new ArrayList<>();
        if (!isCoiZonesEnabled()) return result;
        List<Map<?, ?>> zoneList = config.getMapList("coi-zones.zones");
        for (Map<?, ?> raw : zoneList) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                String name = (String) map.get("name");
                String world = map.containsKey("world") ? (String) map.get("world") : "world";
                int minY = toInt(map.get("min-y"));
                int maxY = toInt(map.get("max-y"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> vertexList = (List<Map<String, Object>>) map.get("vertices");
                if (vertexList == null || vertexList.size() < 3) {
                    PrettyLogger.warn("CoI zone '" + name + "' needs at least 3 vertices — skipping");
                    continue;
                }
                List<double[]> vertices = new ArrayList<>();
                for (Map<String, Object> v : vertexList) {
                    vertices.add(new double[]{toDouble(v.get("x")), toDouble(v.get("z"))});
                }

                boolean alwaysNight = map.containsKey("always-night") && toBoolean(map.get("always-night"));
                boolean lightning = map.containsKey("lightning-effects") && toBoolean(map.get("lightning-effects"));
                int lightningInterval = map.containsKey("lightning-interval-ticks") ? toInt(map.get("lightning-interval-ticks")) : 100;
                int lightningRadius = map.containsKey("lightning-radius") ? toInt(map.get("lightning-radius")) : 30;
                result.add(new CoiZone(name, world, minY, maxY, vertices,
                        alwaysNight, lightning, lightningInterval, lightningRadius));
            } catch (Exception e) {
                PrettyLogger.warn("Failed to load CoI zone: " + e.getMessage());
            }
        }
        return result;
    }

    private int toInt(Object value) {
        if (value instanceof Number num) return num.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }


    public boolean isRecipesEnabled() {
        return config.getBoolean("recipes.enabled", true);
    }

    public int getMaxRecipes() {
        return config.getInt("recipes.max-recipes", 100);
    }

    public boolean isLogRecipeChanges() {
        return config.getBoolean("recipes.log-changes", true);
    }


    public String getTokenItemName() {
        return config.getString("universal-token.item-name", "&6&lUniversal Wrap Token");
    }

    public java.util.List<String> getTokenItemLore() {
        return config.getStringList("universal-token.item-lore");
    }

    public String getGuiMainTitle() {
        return config.getString("universal-token.gui-main-title", "&6Universal Token - Categories");
    }

    public String getGuiCategoryTitle() {
        return config.getString("universal-token.gui-category-title", "&6{category} Wraps");
    }

    public String getGuiConfirmTitle() {
        return config.getString("universal-token.gui-confirm-title", "&cConfirm Exchange?");
    }

    public String getTokenMessage(String key) {
        return config.getString("universal-token.messages." + key, "");
    }


    public String getChatControlTokenName() {
        return config.getString("chatcontrol-token.item-name", "&b&lCustom Message Token");
    }

    public java.util.List<String> getChatControlTokenLore() {
        return config.getStringList("chatcontrol-token.item-lore");
    }

    public String getChatControlMessage(String key) {
        return config.getString("chatcontrol-token.messages." + key, "");
    }


    public boolean isUseColors() {
        return config.getBoolean("logging.use-colors", true);
    }

    public boolean isShowHeader() {
        return config.getBoolean("logging.show-header", true);
    }

    public String getMinLogLevel() {
        return config.getString("logging.min-level", "INFO");
    }

    public boolean isLogCommands() {
        return config.getBoolean("logging.log-commands", true);
    }

    public boolean isLogFeatures() {
        return config.getBoolean("logging.log-features", true);
    }


    public boolean isAsyncProcessing() {
        return config.getBoolean("performance.async-processing", true);
    }

    public boolean isEnableCaching() {
        return config.getBoolean("performance.enable-caching", true);
    }


    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }


    public FileConfiguration getConfig() {
        return config;
    }
}

package net.mysterria.stuff.features.dungeons;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

public class DungeonWorldEnforcer implements Listener {

    private final String worldName;

    public DungeonWorldEnforcer(MysterriaStuff plugin, String worldName) {
        this.worldName = worldName;
        enforceWorldType();
    }

    private void enforceWorldType() {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            // World already loaded — we were beaten to it or it's a fresh server start
            if (existing.getWorldType() != WorldType.FLAT) {
                PrettyLogger.warn("World '" + worldName + "' is already loaded with type "
                        + existing.getWorldType() + " instead of FLAT. "
                        + "Add the dungeon plugin to 'loadbefore' in plugin.yml to fix this.");
            } else {
                PrettyLogger.debug("World '" + worldName + "' already loaded with FLAT type.");
            }
            return;
        }

        PrettyLogger.info("Pre-creating world '" + worldName + "' with FLAT type to override dungeon plugin...");
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            PrettyLogger.success("World '" + worldName + "' pre-created with FLAT type.");
        } else {
            PrettyLogger.warn("Failed to pre-create world '" + worldName + "' — Bukkit returned null.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        if (!event.getWorld().getName().equals(worldName)) return;
        World world = event.getWorld();
        if (world.getWorldType() != WorldType.FLAT) {
            PrettyLogger.warn("World '" + worldName + "' initialized as " + world.getWorldType()
                    + " — FLAT enforcement failed. Ensure MysterriaStuff loads before the dungeon plugin "
                    + "via 'loadbefore' in plugin.yml.");
        } else {
            PrettyLogger.debug("World '" + worldName + "' confirmed FLAT on WorldInitEvent.");
        }
    }
}

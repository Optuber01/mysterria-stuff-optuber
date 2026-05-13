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
        enforceVoidWorld();
    }

    private void enforceVoidWorld() {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            PrettyLogger.debug("World '" + worldName + "' already loaded — skipping pre-creation.");
            return;
        }

        PrettyLogger.info("Pre-creating void world '" + worldName + "' before dungeon plugin...");
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            PrettyLogger.success("Void world '" + worldName + "' pre-created successfully.");
        } else {
            PrettyLogger.warn("Failed to pre-create void world '" + worldName + "' — Bukkit returned null.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        if (!event.getWorld().getName().equals(worldName)) return;
        PrettyLogger.debug("World '" + worldName + "' initialized.");
    }
}

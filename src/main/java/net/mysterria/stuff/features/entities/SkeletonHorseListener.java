package net.mysterria.stuff.features.entities;

import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Applies Mysterria's global taming rule to naturally spawned skeleton horses. */
public final class SkeletonHorseListener implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof SkeletonHorse skeletonHorse)) {
            return;
        }
        if (!skeletonHorse.isTrapped() && !skeletonHorse.isTamed()) {
            skeletonHorse.setTamed(true);
        }
    }
}

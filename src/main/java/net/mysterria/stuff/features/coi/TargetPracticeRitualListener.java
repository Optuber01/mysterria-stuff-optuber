package net.mysterria.stuff.features.coi;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class TargetPracticeRitualListener implements Listener {

    private static final int TARGET_PRACTICE_REQUIRED_TYPES = 12;

    private final MysterriaStuff plugin;

    public TargetPracticeRitualListener(MysterriaStuff plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!isTargetPracticeRelevant(player, event)) {
            return;
        }

        String projectileType = classifyProjectile(event.getEntity());
        if (projectileType == null) {
            return;
        }

        int completedTypes = TargetPracticeRitualTracker.recordTargetPracticeType(player, projectileType);
        if (completedTypes >= TARGET_PRACTICE_REQUIRED_TYPES) {
            TargetPracticeRitualTracker.markCustomCompleted(player, TargetPracticeRitualTracker.TARGET_PRACTICE_ID);
            player.sendMessage(Component.text("Target Practice ritual completed.", NamedTextColor.GREEN));
            return;
        }

        player.sendActionBar(Component.text(
                "Target Practice: " + completedTypes + "/" + TARGET_PRACTICE_REQUIRED_TYPES,
                NamedTextColor.YELLOW
        ));
    }

    private boolean isTargetPracticeRelevant(Player player, ProjectileHitEvent event) {
        if (TargetPracticeRitualTracker.isAdvancementSatisfied(player, TargetPracticeRitualTracker.TARGET_PRACTICE_ID)) {
            return false;
        }

        CircleOfImaginationAPI api = plugin.getCoiAPI();
        if (api == null || !api.isBeyonder(player)) {
            return false;
        }

        int targetSequence = api.getLowestSequence(player) - 1;
        if (targetSequence < 0) {
            return false;
        }

        if (!api.hasAssignedRitual(player, targetSequence, TargetPracticeRitualTracker.TARGET_PRACTICE_ID)) {
            return false;
        }

        Block hitBlock = event.getHitBlock();
        return hitBlock != null && hitBlock.getType() == Material.TARGET;
    }

    private String classifyProjectile(Projectile projectile) {
        if (projectile instanceof SpectralArrow) return "spectral_arrow";
        if (projectile instanceof Arrow arrow) {
            return arrow.getBasePotionType() != null ? "tipped_arrow" : "arrow";
        }
        if (projectile instanceof Trident) return "trident";
        if (projectile instanceof Snowball) return "snowball";
        if (projectile instanceof Egg) return "egg";
        if (projectile instanceof EnderPearl) return "ender_pearl";
        if (projectile instanceof ThrownExpBottle) return "experience_bottle";
        if (projectile instanceof SplashPotion) return "splash_potion";
        if (projectile instanceof LingeringPotion) return "lingering_potion";
        if (projectile instanceof Firework) return "firework";
        if (projectile instanceof WindCharge) return "wind_charge";
        if (projectile instanceof FishHook) return "fishing_hook";
        return null;
    }
}

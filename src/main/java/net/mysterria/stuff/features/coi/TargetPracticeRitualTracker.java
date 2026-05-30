package net.mysterria.stuff.features.coi;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class TargetPracticeRitualTracker {

    static final String TARGET_PRACTICE_ID = "blazeandcave:redstone/target_practise";

    private static final NamespacedKey CUSTOM_COMPLETIONS_KEY =
            new NamespacedKey("circleofimagination", "ritual_custom_completions");
    private static final NamespacedKey TARGET_PRACTICE_TYPES_KEY =
            new NamespacedKey("circleofimagination", "ritual_target_practice_types");

    private TargetPracticeRitualTracker() {
    }

    static boolean isAdvancementSatisfied(Player player, String advancementId) {
        return isAdvancementDone(player, advancementId) || isCustomCompleted(player, advancementId);
    }

    static boolean isCustomCompleted(Player player, String advancementId) {
        return getCsvSet(player.getPersistentDataContainer(), CUSTOM_COMPLETIONS_KEY).contains(advancementId);
    }

    static void markCustomCompleted(Player player, String advancementId) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Set<String> completed = getCsvSet(pdc, CUSTOM_COMPLETIONS_KEY);
        if (completed.add(advancementId)) {
            setCsvSet(pdc, CUSTOM_COMPLETIONS_KEY, completed);
        }
    }

    static int recordTargetPracticeType(Player player, String projectileType) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Set<String> completedTypes = getCsvSet(pdc, TARGET_PRACTICE_TYPES_KEY);
        if (projectileType != null && completedTypes.add(projectileType)) {
            setCsvSet(pdc, TARGET_PRACTICE_TYPES_KEY, completedTypes);
        }
        return completedTypes.size();
    }

    static void clearTargetPracticeProgress(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(TARGET_PRACTICE_TYPES_KEY);

        Set<String> completed = getCsvSet(pdc, CUSTOM_COMPLETIONS_KEY);
        if (completed.remove(TARGET_PRACTICE_ID)) {
            setCsvSet(pdc, CUSTOM_COMPLETIONS_KEY, completed);
        }
    }

    private static boolean isAdvancementDone(Player player, String advancementId) {
        NamespacedKey key = NamespacedKey.fromString(advancementId);
        if (key == null) {
            return false;
        }
        var advancement = org.bukkit.Bukkit.getAdvancement(key);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    private static Set<String> getCsvSet(PersistentDataContainer pdc, NamespacedKey key) {
        String raw = pdc.get(key, PersistentDataType.STRING);
        Set<String> values = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }

        values.addAll(Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList());
        return values;
    }

    private static void setCsvSet(PersistentDataContainer pdc, NamespacedKey key, Set<String> values) {
        if (values.isEmpty()) {
            pdc.remove(key);
            return;
        }
        pdc.set(key, PersistentDataType.STRING, String.join(",", values));
    }
}

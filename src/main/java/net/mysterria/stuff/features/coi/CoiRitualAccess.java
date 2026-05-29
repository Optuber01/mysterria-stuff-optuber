package net.mysterria.stuff.features.coi;

import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;

final class CoiRitualAccess {

    private static boolean lookupAttempted;
    private static boolean warned;
    private static Method beyonderOfMethod;
    private static Method ritualsForAdvancementMethod;

    private CoiRitualAccess() {
    }

    static boolean hasAssignedRitual(Player player, int targetSequence, String ritualId) {
        if (player == null || ritualId == null || ritualId.isBlank()) {
            return false;
        }

        if (!ensureLookup()) {
            return false;
        }

        try {
            Object beyonder = beyonderOfMethod.invoke(null, player);
            if (beyonder == null) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<String> assigned = (List<String>) ritualsForAdvancementMethod.invoke(beyonder, targetSequence);
            return assigned != null && assigned.contains(ritualId);
        } catch (ReflectiveOperationException exception) {
            warnOnce("Failed to read CoI ritual assignments: " + exception.getMessage());
            return false;
        }
    }

    private static boolean ensureLookup() {
        if (lookupAttempted) {
            return beyonderOfMethod != null && ritualsForAdvancementMethod != null;
        }

        lookupAttempted = true;
        try {
            Class<?> beyonderClass = Class.forName("dev.ua.ikeepcalm.coi.domain.beyonder.model.Beyonder");
            beyonderOfMethod = beyonderClass.getMethod("of", Player.class);
            ritualsForAdvancementMethod = beyonderClass.getMethod("getRitualsForAdvancement", int.class);
            return true;
        } catch (ReflectiveOperationException exception) {
            warnOnce("Unable to hook CoI ritual assignments: " + exception.getMessage());
            return false;
        }
    }

    private static void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        PrettyLogger.warn(message);
    }
}

package net.mysterria.stuff.audit;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import dev.ua.ikeepcalm.coi.api.audit.AuditEmission;
import dev.ua.ikeepcalm.coi.api.audit.AuditOutcome;
import dev.ua.ikeepcalm.coi.api.audit.AuditPrivacy;
import dev.ua.ikeepcalm.coi.api.audit.AuditRisk;
import dev.ua.ikeepcalm.coi.api.audit.MysterriaAudit;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Best-effort bridge to the optional shared Mysterria audit ledger. */
public final class StuffAuditEmitter {
    private static final String NAMESPACE = "mysterria-stuff.";
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_TEXT = 256;

    private StuffAuditEmitter() {
    }

    /**
     * Auditing is optional. This method only enqueues an immutable emission on
     * the provider's side and never gates gameplay or the local store.
     */
    public static void emit(JavaPlugin plugin, String operation, AuditOutcome outcome,
                            AuditRisk risk, UUID correlationId, String businessId,
                            UUID actorId, UUID subjectId, UUID targetId, String reason,
                            Map<String, ?> values) {
        if (operation == null || operation.isBlank() || outcome == null || risk == null
                || correlationId == null || businessId == null || businessId.isBlank()) {
            return;
        }

        try {
            RegisteredServiceProvider<MysterriaAudit> registration =
                    Bukkit.getServicesManager().getRegistration(MysterriaAudit.class);
            MysterriaAudit audit = registration == null ? null : registration.getProvider();
            if (audit == null) return;

            audit.emit(new AuditEmission(
                    NAMESPACE + operation,
                    outcome,
                    risk,
                    AuditPrivacy.STAFF_RESTRICTED,
                    correlationId,
                    businessId,
                    actorId,
                    subjectId,
                    targetId,
                    reason,
                    boundedMetadata(values)));
        } catch (RuntimeException | LinkageError failure) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "Mysterria audit emission was unavailable", failure);
            }
        }
    }

    public static UUID correlationId() {
        return UUID.randomUUID();
    }

    public static String tokenBusinessId(String tokenType) {
        return "token:" + safe(tokenType);
    }

    public static String wrapBusinessId(Wrap wrap, String fallback) {
        if (wrap == null) return "wrap:" + safe(fallback);
        String id = wrap.getUuid();
        if (id == null || id.isBlank()) id = fallback;
        if (id == null || id.isBlank()) id = wrap.getWrapName();
        return "wrap:" + safe(id);
    }

    public static Map<String, Object> tokenMetadata(String tokenType, int amount, String delivery) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("token_type", safe(tokenType));
        values.put("amount", amount);
        if (delivery != null) values.put("delivery", safe(delivery));
        return values;
    }

    public static Map<String, Object> wrapMetadata(Wrap wrap, ItemStack item, boolean physical) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wrap_id", safe(wrap == null ? null : wrap.getUuid()));
        values.put("wrap_name", safe(wrap == null ? null : wrap.getWrapName()));
        values.put("physical", physical);
        if (item != null) {
            values.put("item_type", item.getType().getKey().toString());
            values.put("item_amount", item.getAmount());
        }
        return values;
    }

    private static Map<String, Object> boundedMetadata(Map<String, ?> values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (values == null) return metadata;
        values.forEach((key, value) -> {
            if (metadata.size() >= MAX_METADATA_ENTRIES || key == null
                    || !key.matches("[a-z][a-z0-9_]*") || value == null) return;
            metadata.put(key, boundedValue(value));
        });
        return Map.copyOf(metadata);
    }

    private static Object boundedValue(Object value) {
        if (value instanceof String text) return safe(text);
        if (value instanceof Number || value instanceof Boolean) return value;
        return safe(String.valueOf(value));
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}

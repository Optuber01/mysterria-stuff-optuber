package net.mysterria.stuff.audit;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditProducer;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Best-effort bridge to the optional shared Mysterria audit ledger. */
public final class StuffAuditEmitter {
    private static final String NAMESPACE = "mysterria-stuff.";
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_TEXT = 256;
    private static volatile AuditProducer producer;

    private StuffAuditEmitter() {
    }

    public static void initialize(JavaPlugin plugin) {
        producer = AuditProducer.create(plugin.getDataFolder().toPath().toAbsolutePath().getParent()
                        .resolve("mysterria-audit-spool"),
                "mysterria-stuff", plugin.getPluginMeta().getVersion());
    }

    public static void close() {
        AuditProducer current = producer;
        producer = null;
        if (current != null) current.close();
    }

    /**
     * Auditing is optional. This method only enqueues an immutable emission on
     * the provider's side and never gates gameplay or the local store. Callers
     * must only use this after the state change has been applied and persisted.
     */
    public static void emit(String operation,
                            UUID correlationId, String businessId,
                            UUID actorId, UUID subjectId, UUID targetId, String reason,
                            Map<String, ?> values) {
        emit(AuditOutcome.COMMITTED, operation, correlationId, businessId, actorId, subjectId,
                targetId, reason, values);
    }

    /** Records an operation that was attempted but did not take effect. */
    public static void emitFailed(String operation,
                                  UUID correlationId, String businessId,
                                  UUID actorId, UUID subjectId, UUID targetId, String reason,
                                  Map<String, ?> values) {
        emit(AuditOutcome.FAILED, operation, correlationId, businessId, actorId, subjectId,
                targetId, reason, values);
    }

    private static void emit(AuditOutcome outcome, String operation,
                             UUID correlationId, String businessId,
                             UUID actorId, UUID subjectId, UUID targetId, String reason,
                             Map<String, ?> values) {
        if (operation == null || operation.isBlank() || correlationId == null
                || businessId == null || businessId.isBlank()) {
            return;
        }

        try {
            AuditProducer current = producer;
            if (current == null) return;

            Map<String, Object> metadata = new LinkedHashMap<>();
            if (actorId == null) metadata.put("actor_name", "console");
            if (values != null) metadata.putAll(values);
            current.emit(NAMESPACE + operation, outcome, AuditRisk.NORMAL,
                    AuditPrivacy.STAFF_RESTRICTED, correlationId, businessId, actorId,
                    subjectId, targetId, reason, boundedMetadata(metadata));
        } catch (Throwable failure) {
            AuditProducer current = producer;
            if (current != null) current.recordFailure();
        }
    }

    /** Actor UUID for a command sender; null for console and other non-player senders. */
    public static UUID actorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    /** Position metadata (world, x, y, z) for a player; empty when the player is absent. */
    public static Map<String, Object> location(Player player) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (player == null) return values;
        Location location = player.getLocation();
        if (location.getWorld() != null) values.put("world", location.getWorld().getName());
        values.put("x", location.getBlockX());
        values.put("y", location.getBlockY());
        values.put("z", location.getBlockZ());
        return values;
    }

    /** Lowercase hex SHA-256 of the given text, for fingerprinting content without storing it. */
    public static String sha256(String text) {
        if (text == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            return null;
        }
    }

    public static UUID correlationId() {
        return UUID.randomUUID();
    }

    public static String tokenBusinessId(String tokenType) {
        return "token:" + safe(tokenType);
    }

    public static String wrapBusinessId(String wrapId, String fallback) {
        String id = wrapId;
        if (id == null || id.isBlank()) id = fallback;
        return "wrap:" + safe(id);
    }

    public static Map<String, Object> tokenMetadata(String tokenType, int amount, String delivery) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("token_type", safe(tokenType));
        values.put("amount", amount);
        if (delivery != null) values.put("delivery", safe(delivery));
        return values;
    }

    public static Map<String, Object> wrapMetadata(String wrapId, String wrapName,
                                                    String itemType, int itemAmount,
                                                    boolean physical) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wrap_id", safe(wrapId));
        values.put("wrap_name", safe(wrapName));
        values.put("physical", physical);
        if (itemType != null) {
            values.put("item_type", safe(itemType));
            values.put("item_amount", itemAmount);
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

package net.mysterria.stuff.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single delivery path for items handed to a player: inventory first, leftovers dropped at the
 * player's feet. Amounts are snapshotted before {@code Inventory.addItem}, which may mutate the
 * passed stacks.
 */
public final class ItemDelivery {

    public static final String KEY_MODE = "delivery_mode";
    public static final String KEY_DELIVERED = "delivered_amount";
    public static final String KEY_DROPPED = "dropped_amount";

    private ItemDelivery() {
    }

    public record Result(int requestedAmount, int deliveredAmount, int droppedAmount) {
        public String mode() {
            if (droppedAmount <= 0) return "inventory";
            return deliveredAmount <= 0 ? "dropped" : "partial";
        }

        public Result plus(Result other) {
            return new Result(requestedAmount + other.requestedAmount,
                    deliveredAmount + other.deliveredAmount, droppedAmount + other.droppedAmount);
        }

        public Map<String, Object> toMetadata() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(KEY_MODE, mode());
            values.put(KEY_DELIVERED, deliveredAmount);
            values.put(KEY_DROPPED, droppedAmount);
            return values;
        }
    }

    public static Result deliver(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return new Result(0, 0, 0);
        int requestedAmount = item.getAmount();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        int droppedAmount = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getAmount() <= 0) continue;
            droppedAmount += leftover.getAmount();
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        int deliveredAmount = Math.max(0, requestedAmount - droppedAmount);
        return new Result(requestedAmount, deliveredAmount, droppedAmount);
    }

    public static Result deliverAll(Player player, List<ItemStack> items) {
        Result total = new Result(0, 0, 0);
        for (ItemStack item : items) {
            total = total.plus(deliver(player, item));
        }
        return total;
    }
}

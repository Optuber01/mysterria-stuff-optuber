package net.mysterria.stuff.audit;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stamps and reads the shared CoI item-identity PDC keys without depending on CoI.
 *
 * <p>Only non-stackable single items (max stack size 1, amount 1) are ever stamped. CoI treats
 * {@code circleofimagination:item_uuid} as a per-instance unique id and its dupe scan flags any uuid
 * whose summed amount exceeds 1, and a per-instance PDC value would also stop otherwise identical
 * items from stacking. Fungible, stackable items such as tokens are therefore never stamped. They
 * are identified per lot instead: {@link #lotMetadata} mints one {@code item_uuid} per granted stack
 * and writes it to the audit row only (with {@code item_mint_qty}, {@code item_origin} and
 * {@code item_minted_by}), leaving the item PDC identical across grants so separately granted
 * stacks keep stacking exactly as they did before auditing was added.
 */
public final class ItemIdentity {

    public static final String ORIGIN_STAFF_GRANT = "STAFF_GRANT";
    public static final String ORIGIN_SHOP = "SHOP";
    /** item_uuid_scope for a per-instance id that is also written to the item PDC. */
    public static final String SCOPE_INSTANCE = "instance";
    /** item_uuid_scope for a row-only id naming one granted stack of unstamped items. */
    public static final String SCOPE_LOT = "lot";

    private static final String NAMESPACE = "circleofimagination";
    private static final NamespacedKey ITEM_UUID = new NamespacedKey(NAMESPACE, "item_uuid");
    private static final NamespacedKey ITEM_ORIGIN = new NamespacedKey(NAMESPACE, "item_origin");
    private static final NamespacedKey ITEM_MINTED_BY = new NamespacedKey(NAMESPACE, "item_minted_by");
    private static final NamespacedKey ITEM_PARENT = new NamespacedKey(NAMESPACE, "item_parent");

    private ItemIdentity() {
    }

    /**
     * Stamps a fresh item_uuid plus origin, and optionally minted_by / parent.
     *
     * @return the stamped item UUID, or null if the item is stackable or has no meta
     */
    public static String stamp(ItemStack item, String origin, String mintedBy, String parentUuid) {
        if (!isUniqueInstance(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String itemUuid = UUID.randomUUID().toString();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_UUID, PersistentDataType.STRING, itemUuid);
        pdc.set(ITEM_ORIGIN, PersistentDataType.STRING, origin);
        if (mintedBy != null) pdc.set(ITEM_MINTED_BY, PersistentDataType.STRING, mintedBy);
        if (parentUuid != null) pdc.set(ITEM_PARENT, PersistentDataType.STRING, parentUuid);
        item.setItemMeta(meta);
        return itemUuid;
    }

    public static String stampShop(ItemStack item, String parentUuid) {
        return stamp(item, ORIGIN_SHOP, null, parentUuid);
    }

    /** True only for items that can never stack, so a per-instance uuid changes nothing visible. */
    public static boolean isUniqueInstance(ItemStack item) {
        return item != null && item.getAmount() == 1 && item.getMaxStackSize() == 1;
    }

    /**
     * Row-only lot identity for one granted stack of fungible items that are deliberately not
     * PDC-stamped. The returned {@code item_uuid} names the lot, not an item instance, and is never
     * written to the item.
     *
     * @param mintedBy actor UUID string, "console", or null to omit
     */
    public static Map<String, Object> lotMetadata(String origin, String mintedBy, int amount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("item_uuid", UUID.randomUUID().toString());
        metadata.put("item_uuid_scope", SCOPE_LOT);
        metadata.put("item_mint_qty", amount);
        metadata.put("item_origin", origin);
        if (mintedBy != null) metadata.put("item_minted_by", mintedBy);
        return metadata;
    }

    public static String readUuid(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        try {
            return item.getItemMeta().getPersistentDataContainer().get(ITEM_UUID, PersistentDataType.STRING);
        } catch (IllegalArgumentException wrongType) {
            return null;
        }
    }
}

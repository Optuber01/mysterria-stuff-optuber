package net.mysterria.stuff.audit;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Stamps and reads the shared CoI item-identity PDC keys without depending on CoI.
 */
public final class ItemIdentity {

    public static final String ORIGIN_STAFF_GRANT = "STAFF_GRANT";
    public static final String ORIGIN_SHOP = "SHOP";

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
     * @return the stamped item UUID, or null if the item has no meta
     */
    public static String stamp(ItemStack item, String origin, String mintedBy, String parentUuid) {
        if (item == null) return null;
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

    /** Staff-granted item; minted_by is the actor UUID or "console". */
    public static String stampStaffGrant(ItemStack item, UUID actorId) {
        return stamp(item, ORIGIN_STAFF_GRANT, actorId == null ? "console" : actorId.toString(), null);
    }

    public static String stampShop(ItemStack item, String parentUuid) {
        return stamp(item, ORIGIN_SHOP, null, parentUuid);
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

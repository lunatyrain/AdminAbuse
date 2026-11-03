package com.adminabuse;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Builds and recognises Luna's Vampire Bite: an iron sword with Curse of Vanishing that stacks to 99. */
public final class BiteItem {
    public static final int MAX_STACK = 99;

    private BiteItem() {
    }

    public static ItemStack create(AdminAbusePlugin plugin, int amount) {
        ItemStack item = new ItemStack(Material.IRON_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Luna's Vampire Bite", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Deals no damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("One bite, then it crumbles to dust.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        // Swords are damageable and so normally can't stack. Remove durability, then raise the stack size.
        item.unsetData(DataComponentTypes.MAX_DAMAGE);
        item.unsetData(DataComponentTypes.DAMAGE);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, MAX_STACK);
        item.setAmount(Math.max(1, Math.min(MAX_STACK, amount)));
        return item;
    }

    public static boolean is(AdminAbusePlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.IRON_SWORD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.itemKey(), PersistentDataType.BYTE);
    }
}

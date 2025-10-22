package com.craftvn.ngoc.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class GemTag {
    public static final String KEY_NAME = "gem-key";
    public static ItemStack tagGem(JavaPlugin plugin, ItemStack item, String gemKey) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING, gemKey);
        item.setItemMeta(meta);
        return item;
    }
    public static String readGem(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, KEY_NAME), PersistentDataType.STRING);
    }
}

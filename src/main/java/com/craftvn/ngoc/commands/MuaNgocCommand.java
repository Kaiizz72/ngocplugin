package com.craftvn.ngoc.commands;

import com.craftvn.ngoc.NgocPlugin;
import com.craftvn.ngoc.util.GemTag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MuaNgocCommand implements CommandExecutor {

    private final NgocPlugin plugin;
    public MuaNgocCommand(NgocPlugin plugin) { this.plugin = plugin; }

    private boolean hasFloodgate() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Class.forName("org.geysermc.cumulus.form.SimpleForm");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        boolean isPE = false;
        if (hasFloodgate()) {
            try {
                isPE = org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
            } catch (Throwable ignored) {}
        }

        if (isPE && hasFloodgate()) openFormPE(p);
        else openChestGUI(p);

        return true;
    }

    private String c(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }

    private void openChestGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, c("&aShop Ngọc"));

        // Border
        org.bukkit.inventory.ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        int[] border = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int s : border) inv.setItem(s, glass);

        int slot = 10;
        for (String key : plugin.getConfig().getConfigurationSection("gems").getKeys(false)) {
            Material mat = Material.matchMaterial(plugin.getConfig().getString("gems."+key+".material"));
            ItemStack it = new ItemStack(mat == null ? Material.PAPER : mat);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(c(plugin.getConfig().getString("gems."+key+".name")));
            List<String> loreRaw = plugin.getConfig().getStringList("gems."+key+".lore");
            List<String> lore = new ArrayList<>();
            for (String l : loreRaw) lore.add(c(l));
            meta.setLore(lore);
            it.setItemMeta(meta);
            GemTag.tagGem(plugin, it, key);
            inv.setItem(slot, it);
            slot++; if (slot % 9 == 8) slot += 2;
        }
        p.openInventory(inv);
    }

    private void openFormPE(Player p) {
        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title("§aShop Ngọc")
                .content("Chọn ngọc muốn mua:");
        List<String> keys = new ArrayList<>(plugin.getConfig().getConfigurationSection("gems").getKeys(false));
        for (String k : keys) {
            String name = c(plugin.getConfig().getString("gems."+k+".name"));
            List<String> lore = plugin.getConfig().getStringList("gems."+k+".lore");
            String info = lore.isEmpty()?"": ChatColor.stripColor(c(lore.get(lore.size()-1)));
            form.button(name + "\n" + info);
        }
        form.validResultHandler((player, result) -> {
            String key = keys.get(result.clickedButtonId());
            // Gửi event mua bằng cách call 1 task sử dụng logic của listener qua API tĩnh hoặc command phụ
            // Ở đây đơn giản dùng event tùy chỉnh: gọi method util của listener thông qua static accessor.
            Bukkit.getScheduler().runTask(plugin, () -> {
                com.craftvn.ngoc.listener.NgocUseListener.tryBuyGem(plugin, p, key);
            });
        });
        org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
    }
}

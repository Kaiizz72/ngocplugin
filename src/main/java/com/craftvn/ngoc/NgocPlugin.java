package com.craftvn.ngoc;

import org.bukkit.plugin.java.JavaPlugin;
import com.craftvn.ngoc.commands.MuaNgocCommand;
import com.craftvn.ngoc.listener.NgocUseListener;

public class NgocPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("muangoc").setExecutor(new MuaNgocCommand(this));
        getServer().getPluginManager().registerEvents(new NgocUseListener(this), this);
        getLogger().info("NgocPlugin đã bật!");
    }
}

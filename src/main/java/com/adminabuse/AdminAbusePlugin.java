package com.adminabuse;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminAbusePlugin extends JavaPlugin {
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "luna_bite");
        getServer().getPluginManager().registerEvents(new BiteListener(this), this);
        BiteCommand cmd = new BiteCommand(this);
        if (getCommand("lunabite") != null) {
            getCommand("lunabite").setExecutor(cmd);
            getCommand("lunabite").setTabCompleter(cmd);
        }
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }
}

package com.adminabuse;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminAbusePlugin extends JavaPlugin {
    private NamespacedKey itemKey;
    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "luna_bite");
        debug = getConfig().getBoolean("debug", false);
        getServer().getPluginManager().registerEvents(new BiteListener(this), this);
        getLogger().info("AdminAbuse " + getPluginMeta().getVersion() + " enabled (bite listens on PrePlayerAttackEntityEvent).");
        BiteCommand cmd = new BiteCommand(this);
        if (getCommand("lunabite") != null) {
            getCommand("lunabite").setExecutor(cmd);
            getCommand("lunabite").setTabCompleter(cmd);
        }
    }

    public boolean debug() {
        return debug;
    }

    public void setDebug(boolean on) {
        debug = on;
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }
}

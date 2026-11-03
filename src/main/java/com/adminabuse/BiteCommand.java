package com.adminabuse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** /lunabite [amount] - gives yourself 1-99 stacked bites. */
public final class BiteCommand implements CommandExecutor, TabCompleter {
    private final AdminAbusePlugin plugin;

    public BiteCommand(AdminAbusePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Only players can hold the bite.", NamedTextColor.RED));
            return true;
        }
        if (!p.hasPermission("adminabuse.give")) {
            p.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
            plugin.setDebug(!plugin.debug());
            p.sendMessage(Component.text("Bite debug: " + (plugin.debug() ? "ON" : "OFF"), NamedTextColor.GRAY));
            return true;
        }
        int amount = plugin.getConfig().getInt("default-amount", 1);
        if (args.length >= 1) {
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                p.sendMessage(Component.text("Usage: /" + label + " [amount 1-" + BiteItem.MAX_STACK + "]", NamedTextColor.RED));
                return true;
            }
        }
        amount = Math.max(1, Math.min(BiteItem.MAX_STACK, amount));

        ItemStack stack = BiteItem.create(plugin, amount);
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
        p.sendMessage(Component.text("Gave yourself " + amount + "x Luna's Vampire Bite.", NamedTextColor.DARK_RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return args.length == 1 ? List.of("1", "16", "64", "99", "debug") : List.of();
    }
}

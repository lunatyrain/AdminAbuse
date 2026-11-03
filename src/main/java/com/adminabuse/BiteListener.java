package com.adminabuse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hitting something with the bite cancels the damage (so nothing is hurt, no armor wears, no totem pops) and instead
 * plays the hurt animation, knockback and the bite effect by hand. One bite uses up one sword from the stack.
 */
public final class BiteListener implements Listener {
    private final AdminAbusePlugin plugin;
    private final Map<UUID, Integer> lastBite = new HashMap<>();

    public BiteListener(AdminAbusePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!BiteItem.is(plugin, hand)) return;

        e.setCancelled(true); // never real damage, not even the sword's sweep
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (!p.hasPermission("adminabuse.use")) return;
        if (p.getAttackCooldown() < 0.9f) return; // weak swings do nothing and don't use up a sword

        int now = Bukkit.getCurrentTick();
        Integer last = lastBite.get(p.getUniqueId());
        if (last != null && now - last < 10) return;
        lastBite.put(p.getUniqueId(), now);

        // single use
        int left = hand.getAmount() - 1;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            p.getInventory().setItemInMainHand(hand);
        }

        // it still "hits": hurt animation, hurt sound and knockback, but zero damage
        Location pl = p.getLocation();
        Location tl = target.getLocation();
        double dx = pl.getX() - tl.getX();
        double dz = pl.getZ() - tl.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            dx = -Math.sin(Math.toRadians(pl.getYaw()));
            dz = Math.cos(Math.toRadians(pl.getYaw()));
        }
        target.playHurtAnimation((float) Math.toDegrees(Math.atan2(-dx, dz)));
        org.bukkit.Sound hurt = target.getHurtSound();
        if (hurt != null) target.getWorld().playSound(tl, hurt, 1.0f, 1.0f);
        target.knockback(plugin.getConfig().getDouble("knockback", 0.45), dx, dz);

        if (target instanceof Player victim) {
            victim.sendMessage(Component.text(plugin.getConfig().getString("message", "You have been bitten by Luna"),
                    NamedTextColor.RED));
        }

        BiteEffect.play(plugin, p, target);

        if (lastBite.size() > 100) lastBite.values().removeIf(t -> now - t > 40);
    }
}

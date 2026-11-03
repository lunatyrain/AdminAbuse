package com.adminabuse;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Swinging the bite at something is handled on the attack itself (before any damage exists), so it works on every
 * living target - creative players, players in damage cooldown, protected areas - and never deals real damage.
 * The flinch, sound, knockback and bite effect are played by hand. One bite uses up one sword from the stack.
 */
public final class BiteListener implements Listener {
    private static final int MIN_GAP_TICKS = 8; // stops one click burning several swords
    private final AdminAbusePlugin plugin;
    private final Map<UUID, Integer> lastBite = new HashMap<>();

    public BiteListener(AdminAbusePlugin plugin) {
        this.plugin = plugin;
    }

    private void dbg(Player p, String msg) {
        if (plugin.debug()) p.sendMessage(Component.text("[AdminAbuse debug] " + msg, NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!BiteItem.is(plugin, hand)) {
            if (plugin.debug() && hand.getType() == org.bukkit.Material.IRON_SWORD) {
                dbg(p, "holding an iron sword, but it is not a bite sword (missing tag).");
            }
            return;
        }
        dbg(p, "swing at " + e.getAttacked().getType() + ", cancelled by another plugin first: " + e.isCancelled());
        if (e.isCancelled() && !plugin.getConfig().getBoolean("ignore-protection", true)) return;

        e.setCancelled(true); // the normal attack never happens, so there is no damage to cancel later
        if (!(e.getAttacked() instanceof LivingEntity target)) {
            dbg(p, "target is not a living entity.");
            return;
        }
        if (!p.hasPermission("adminabuse.use")) {
            dbg(p, "missing permission adminabuse.use.");
            return;
        }

        int now = Bukkit.getCurrentTick();
        Integer last = lastBite.get(p.getUniqueId());
        if (last != null && now - last < MIN_GAP_TICKS) {
            dbg(p, "too soon after the last bite.");
            return;
        }
        lastBite.put(p.getUniqueId(), now);
        if (lastBite.size() > 100) lastBite.values().removeIf(t -> now - t > 40);

        // single use
        int left = hand.getAmount() - 1;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            p.getInventory().setItemInMainHand(hand);
        }
        p.resetCooldown();

        // it still "hits": flinch, hurt sound and knockback, but zero damage
        Location pl = p.getLocation();
        Location tl = target.getLocation();
        double dx = pl.getX() - tl.getX();
        double dz = pl.getZ() - tl.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            dx = -Math.sin(Math.toRadians(pl.getYaw()));
            dz = Math.cos(Math.toRadians(pl.getYaw()));
            len = 1.0;
        }
        dx /= len;
        dz /= len;

        try {
            target.playHurtAnimation((float) Math.toDegrees(Math.atan2(-dx, dz)));
            Sound hurt = target.getHurtSound();
            if (hurt != null) target.getWorld().playSound(tl, hurt, 1.0f, 1.0f);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not play the hurt animation: " + ex);
        }

        // knockback through setVelocity so the client of a hit player is always told about it
        double str = plugin.getConfig().getDouble("knockback", 0.45);
        Vector v = target.getVelocity();
        double vy = target.isOnGround() ? Math.min(0.4, v.getY() / 2.0 + str) : v.getY();
        target.setVelocity(new Vector(v.getX() / 2.0 - dx * str, vy, v.getZ() / 2.0 - dz * str));

        if (target instanceof Player victim) {
            victim.sendMessage(Component.text(plugin.getConfig().getString("message", "You have been bitten by Luna"),
                    NamedTextColor.RED));
        }

        BiteEffect.play(plugin, p, target);
        dbg(p, "bite on " + target.getName() + " done, " + Math.max(0, left) + " left.");
    }

    /** Backup: whatever route a hit takes (sweep, other plugins), the bite never does real damage. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && BiteItem.is(plugin, p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
        }
    }
}

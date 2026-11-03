package com.adminabuse;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The vampire bite, about one second long:
 * <ol>
 *   <li>0-5 ticks: a pair of jaws with bone-white teeth and two long fangs snaps shut on the victim's neck, from the side the attacker stands on.</li>
 *   <li>tick 5: the bite lands. Blood sprays out of two puncture marks, a dark red shockwave ring rolls out.</li>
 *   <li>5-19 ticks: blood spirals up around the victim while a stream of blood is drained into the attacker, who gets hearts at the end.</li>
 * </ol>
 */
public final class BiteEffect {
    private static final Particle.DustOptions BONE = new Particle.DustOptions(Color.fromRGB(246, 240, 232), 0.75f);
    private static final Particle.DustOptions GUM = new Particle.DustOptions(Color.fromRGB(110, 0, 12), 1.0f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 0, 18), 1.15f);
    private static final Particle.DustTransition RING =
            new Particle.DustTransition(Color.fromRGB(200, 0, 28), Color.fromRGB(25, 0, 6), 1.4f);
    private static final Particle.DustTransition STREAM =
            new Particle.DustTransition(Color.fromRGB(230, 10, 35), Color.fromRGB(70, 0, 10), 1.0f);

    private static final int SNAP = 5;   // tick the jaws close
    private static final int END = 20;   // last tick

    private BiteEffect() {
    }

    public static void play(Plugin plugin, Player attacker, LivingEntity target) {
        World w = target.getWorld();
        w.playSound(target.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.6f);
        w.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);

        new BukkitRunnable() {
            int t = 0;
            Location lastBase = target.getLocation().clone();
            Location lastAtk = attacker.getLocation().clone();

            @Override
            public void run() {
                if (t > END) {
                    cancel();
                    return;
                }
                if (target.isValid()) lastBase = target.getLocation().clone();
                if (attacker.isOnline() && attacker.isValid()) lastAtk = attacker.getLocation().clone();
                World world = lastBase.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                // horizontal direction target -> attacker, and "right" across it
                Vector toward = new Vector(lastAtk.getX() - lastBase.getX(), 0, lastAtk.getZ() - lastBase.getZ());
                if (toward.lengthSquared() < 1.0E-4) toward = new Vector(0, 0, 1);
                toward.normalize();
                Vector right = new Vector(-toward.getZ(), 0, toward.getX());
                Vector up = new Vector(0, 1, 0);

                double eye = target.getEyeHeight();
                Location neck = lastBase.clone().add(0, Math.max(0.5, eye - 0.3), 0);
                Location mouth = neck.clone().add(toward.clone().multiply(0.3));

                if (t < SNAP) jaws(world, mouth, right, up, t);
                if (t == SNAP) impact(world, neck, mouth, right, toward, up);
                if (t >= SNAP) {
                    double p = (t - SNAP) / (double) (END - SNAP); // 0..1
                    ring(world, neck, (t - SNAP) * 0.13);
                    helix(world, lastBase, t, p);
                    stream(world, neck, lastAtk.clone().add(0, 1.1, 0), p);
                }
                if (t == 9 || t == 13) {
                    world.playSound(neck, Sound.ENTITY_GENERIC_DRINK, 0.8f, 0.55f);
                }
                if (t == END) {
                    world.spawnParticle(Particle.HEART, lastAtk.clone().add(0, 2.1, 0), 4, 0.35, 0.15, 0.35, 0);
                    world.playSound(lastAtk, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.0f);
                    world.playSound(lastAtk, Sound.ENTITY_PLAYER_BURP, 0.5f, 0.6f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Upper and lower teeth closing on the neck. open goes 0.55 -> 0. */
    private static void jaws(World w, Location mouth, Vector right, Vector up, int t) {
        double open = 0.55 * (1.0 - (t + 1) / (double) SNAP);
        for (int i = -3; i <= 3; i++) {
            double x = i * 0.085;
            double curve = 0.07 * (1.0 - (x / 0.3) * (x / 0.3)); // jaw is slightly arched
            for (int sign = -1; sign <= 1; sign += 2) {
                Location tooth = mouth.clone().add(right.clone().multiply(x)).add(up.clone().multiply(sign * (open + curve)));
                w.spawnParticle(Particle.DUST, tooth, 1, 0, 0, 0, 0, BONE);
                // dark gum line just behind the teeth
                Location gum = tooth.clone().add(up.clone().multiply(sign * 0.07));
                w.spawnParticle(Particle.DUST, gum, 1, 0, 0, 0, 0, GUM);
            }
        }
        // the two long upper fangs
        for (int side = -1; side <= 1; side += 2) {
            Location root = mouth.clone().add(right.clone().multiply(side * 0.1)).add(up.clone().multiply(open + 0.07));
            for (int k = 1; k <= 4; k++) {
                Location tip = root.clone().subtract(up.clone().multiply(k * 0.045));
                w.spawnParticle(Particle.DUST, tip, 1, 0, 0, 0, 0, k == 4 ? GUM : BONE);
            }
        }
    }

    /** The bite lands: blood from the two puncture marks. */
    private static void impact(World w, Location neck, Location mouth, Vector right, Vector toward, Vector up) {
        w.playSound(neck, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        w.playSound(neck, Sound.ENTITY_GENERIC_EAT, 1.0f, 0.5f);
        w.playSound(neck, Sound.ENTITY_BAT_HURT, 0.8f, 0.5f);
        w.spawnParticle(Particle.BLOCK, neck, 28, 0.18, 0.12, 0.18, 0.15, Material.REDSTONE_BLOCK.createBlockData());
        w.spawnParticle(Particle.DAMAGE_INDICATOR, neck, 6, 0.2, 0.15, 0.2, 0.05);
        for (int side = -1; side <= 1; side += 2) {
            Location mark = neck.clone().add(toward.clone().multiply(0.12)).add(right.clone().multiply(side * 0.1));
            w.spawnParticle(Particle.DUST, mark, 8, 0.03, 0.03, 0.03, 0, BLOOD);
            // spray outward and downward
            for (int k = 0; k < 5; k++) {
                Location drop = mark.clone().add(toward.clone().multiply(k * 0.07)).subtract(up.clone().multiply(k * 0.05 * k * 0.4));
                w.spawnParticle(Particle.DUST, drop, 1, 0.02, 0.02, 0.02, 0, BLOOD);
            }
        }
    }

    /** Expanding horizontal shockwave, bright red fading to near-black. */
    private static void ring(World w, Location center, double radius) {
        int points = 18;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, RING);
        }
    }

    /** Two strands of blood spiralling up around the victim. */
    private static void helix(World w, Location base, int t, double p) {
        double h = p * 2.0;
        double radius = 0.7 - 0.25 * p;
        for (int strand = 0; strand < 2; strand++) {
            double a = t * 0.65 + strand * Math.PI;
            Location l = base.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
            w.spawnParticle(Particle.DUST, l, 1, 0.02, 0.02, 0.02, 0, BLOOD);
            w.spawnParticle(Particle.CRIMSON_SPORE, l, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** Blood pulled from the neck into the attacker. */
    private static void stream(World w, Location from, Location to, double p) {
        Vector line = to.toVector().subtract(from.toVector());
        for (int k = 0; k < 4; k++) {
            double f = p - k * 0.1;
            if (f < 0 || f > 1) continue;
            double wobble = Math.sin(f * 12 + k) * 0.12 * (1 - f);
            Location l = from.clone().add(line.clone().multiply(f)).add(wobble, Math.cos(f * 9 + k) * 0.1, -wobble);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 2, 0.03, 0.03, 0.03, 0, STREAM);
        }
    }
}

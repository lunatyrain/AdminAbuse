package com.adminabuse;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bite is a real 3D model, no particles: an upper and a lower jaw built from block display entities - a
 * horseshoe of bone-white teeth with two long fangs on top and two canines below, set in red gums. The jaws
 * lunge in from the attacker's side, snap shut around the victim's neck, clench and shake while blood drips from
 * the fang tips, then open and shrink away.
 */
public final class BiteEffect {
    private static final float R = 0.55f;                          // radius of the tooth horseshoe
    private static final float OPEN = (float) Math.toRadians(38);  // how wide the mouth starts
    private static final float SLIDE = 0.45f;                      // how far in front the jaws start
    private static final Vector3f PIVOT = new Vector3f(0, 0, -0.30f); // the jaw hinge
    private static final int SNAP = 4;   // tick the jaws are shut
    private static final int END = 21;   // tick everything is removed

    private static final Set<Display> ACTIVE = new HashSet<>();

    private BiteEffect() {
    }

    /** Removes any bite models still in the world (server shutdown / reload). */
    public static void clear() {
        for (Display d : new ArrayList<>(ACTIVE)) d.remove();
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ model

    private static final class Part {
        BlockDisplay d;
        Vector3f center;
        Quaternionf rot;
        Vector3f scale;
        boolean upper;
        boolean tip;
        BlockData data;
    }

    private static List<Part> buildModel() {
        List<Part> out = new ArrayList<>();
        float step = (float) Math.toRadians(22);
        for (int i = -4; i <= 4; i++) tooth(out, true, i * step, Math.abs(i) == 1);          // upper: fangs at +-22 deg
        for (int i = -4; i < 4; i++) tooth(out, false, (i + 0.5f) * step, i == -1 || i == 0); // lower: canines at +-11 deg
        gum(out, true, -4 * step, 4 * step, 8);
        gum(out, false, -3.5f * step, 3.5f * step, 7);
        return out;
    }

    /** One tooth: a stack of shrinking cubes, so it tapers to a point. */
    private static void tooth(List<Part> out, boolean upper, float phi, boolean big) {
        float[] hs = big ? (upper ? new float[]{0.14f, 0.12f, 0.10f} : new float[]{0.09f, 0.08f, 0.07f})
                : new float[]{0.07f, 0.06f};
        float[] ws = big ? new float[]{0.10f, 0.07f, 0.04f} : new float[]{0.08f, 0.045f};
        float sign = upper ? -1f : 1f; // upper teeth hang down, lower teeth point up
        float y = 0;
        for (int k = 0; k < hs.length; k++) {
            Part p = new Part();
            p.upper = upper;
            p.center = new Vector3f((float) (R * Math.sin(phi)), sign * (y + hs[k] / 2f), (float) (R * Math.cos(phi)));
            p.rot = new Quaternionf().rotateY(phi);
            p.scale = new Vector3f(ws[k], hs[k], ws[k]);
            p.tip = big && k == hs.length - 1;
            p.data = Material.CALCITE.createBlockData();
            out.add(p);
            y += hs[k];
        }
    }

    /** The red gum line the teeth sit in. */
    private static void gum(List<Part> out, boolean upper, float from, float to, int n) {
        for (int s = 0; s < n; s++) {
            float a = from + (to - from) * s / n;
            float b = from + (to - from) * (s + 1) / n;
            float m = (a + b) / 2f;
            Part p = new Part();
            p.upper = upper;
            p.center = new Vector3f((float) (R * Math.sin(m)), upper ? 0.055f : -0.055f, (float) (R * Math.cos(m)));
            p.rot = new Quaternionf().rotateY(m);
            p.scale = new Vector3f((float) (2 * R * Math.sin((b - a) / 2f) * 1.1), 0.11f, 0.14f);
            p.data = Material.NETHER_WART_BLOCK.createBlockData();
            out.add(p);
        }
    }

    /** The transformation of a part with the mouth open by {@code open} radians. */
    private static Transformation pose(Part p, float open, float slide, float shrink) {
        Quaternionf qx = new Quaternionf().rotateX(p.upper ? -open : open); // upper jaw swings up, lower swings down
        Vector3f c = new Vector3f(p.center).sub(PIVOT);
        qx.transform(c);
        c.add(PIVOT).add(0, 0, slide);
        Quaternionf rot = new Quaternionf(qx).mul(p.rot);
        Vector3f sc = new Vector3f(p.scale).mul(Math.max(shrink, 0.001f));
        return boxAt(c, rot, sc);
    }

    /** A block display is anchored at its corner; this puts its centre at {@code center}. */
    private static Transformation boxAt(Vector3f center, Quaternionf rot, Vector3f scale) {
        Vector3f half = new Vector3f(scale).mul(0.5f);
        rot.transform(half);
        return new Transformation(new Vector3f(center).sub(half), new Quaternionf(rot), new Vector3f(scale), new Quaternionf());
    }

    private static BlockDisplay spawn(World w, Location at, BlockData data, Transformation tf) {
        BlockDisplay d = w.spawn(at, BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setTransformation(tf);
            bd.setBrightness(new Display.Brightness(15, 15));
            bd.setInterpolationDuration(0);
            bd.setTeleportDuration(1);
            bd.setViewRange(1.5f);
            bd.setShadowRadius(0f);
            bd.setPersistent(false);
        });
        ACTIVE.add(d);
        return d;
    }

    private static void animate(BlockDisplay d, Transformation tf, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(tf);
    }

    // ----------------------------------------------------------------- effect

    public static void play(Plugin plugin, Player attacker, LivingEntity target) {
        World w = target.getWorld();
        List<Part> parts = buildModel();
        List<BlockDisplay> drops = new ArrayList<>();
        float[][] dropAt = {{-0.13f, 0.02f, 0.30f}, {0.13f, 0.02f, 0.30f}, {-0.05f, -0.02f, 0.34f}, {0.05f, -0.02f, 0.34f}};

        Location start = anchor(target, attacker.getLocation(), target.getLocation());
        for (Part p : parts) p.d = spawn(w, start, p.data, pose(p, OPEN, SLIDE, 0.25f));

        new BukkitRunnable() {
            int t = 0;
            Location lastTarget = target.getLocation().clone();
            Location lastAtk = attacker.getLocation().clone();

            @Override
            public void run() {
                if (target.isValid()) lastTarget = target.getLocation().clone();
                if (attacker.isOnline() && attacker.isValid()) lastAtk = attacker.getLocation().clone();
                Location a = anchor(target, lastAtk, lastTarget);

                if (t >= END || a.getWorld() == null) {
                    finish(parts, drops);
                    cancel();
                    return;
                }
                for (Part p : parts) if (p.d.isValid()) p.d.teleport(a);
                for (BlockDisplay d : drops) if (d.isValid()) d.teleport(a);

                if (t == 0) {
                    w.playSound(a, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.6f);
                }
                if (t == 1) { // the jaws lunge in and slam shut
                    w.playSound(a, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);
                    for (Part p : parts) animate(p.d, pose(p, 0f, 0f, 1f), SNAP - 1);
                }
                if (t == SNAP) { // bite lands
                    w.playSound(a, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
                    w.playSound(a, Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.6f);
                    w.playSound(a, Sound.ENTITY_GENERIC_EAT, 1.0f, 0.5f);
                    w.playSound(a, Sound.ENTITY_BAT_HURT, 0.8f, 0.5f);
                    for (Part p : parts) if (p.tip) p.d.setBlock(Material.REDSTONE_BLOCK.createBlockData()); // bloody fangs
                    for (float[] pos : dropAt) {
                        Vector3f c = new Vector3f(pos[0], pos[1], pos[2]);
                        drops.add(spawn(w, a, Material.REDSTONE_BLOCK.createBlockData(),
                                boxAt(c, new Quaternionf(), new Vector3f(0.07f))));
                    }
                }
                if (t == SNAP + 1) { // blood runs down
                    for (int i = 0; i < drops.size(); i++) {
                        float[] pos = dropAt[i];
                        Vector3f c = new Vector3f(pos[0], pos[1] - 1.1f - 0.1f * i, pos[2]);
                        animate(drops.get(i), boxAt(c, new Quaternionf(), new Vector3f(0.04f)), 10 + 2 * i);
                    }
                }
                if (t >= SNAP + 1 && t < 15 && (t % 2) == 1) { // the jaws clench and shake
                    float jolt = ((t / 2) % 2 == 0) ? 0.03f : -0.03f;
                    for (Part p : parts) animate(p.d, pose(p, -0.06f, jolt, 1f), 2);
                }
                if (t == 9 || t == 13) w.playSound(a, Sound.ENTITY_GENERIC_DRINK, 0.8f, 0.55f);
                if (t == 15) { // let go: the mouth opens and the model shrinks away
                    w.playSound(a, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 0.8f);
                    for (Part p : parts) animate(p.d, pose(p, OPEN * 0.8f, 0.4f, 0f), END - 16);
                }
                if (t == 17) w.playSound(lastAtk, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.0f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void finish(List<Part> parts, List<BlockDisplay> drops) {
        for (Part p : parts) {
            if (p.d != null) {
                ACTIVE.remove(p.d);
                p.d.remove();
            }
        }
        for (BlockDisplay d : drops) {
            ACTIVE.remove(d);
            d.remove();
        }
    }

    /** At the victim's neck, facing the attacker (the model's front, +z, points at the attacker). */
    private static Location anchor(LivingEntity target, Location attacker, Location targetLoc) {
        double dx = attacker.getX() - targetLoc.getX();
        double dz = attacker.getZ() - targetLoc.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            dx = 0;
            dz = 1;
        }
        Location l = targetLoc.clone().add(0, Math.max(0.5, target.getEyeHeight() - 0.25), 0);
        l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        l.setPitch(0f);
        return l;
    }
}

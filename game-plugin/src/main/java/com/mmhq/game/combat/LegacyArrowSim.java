package com.mmhq.game.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Simulates a legacy (1.8-style) arrow with custom physics to avoid
 * backend differences (e.g., 1.21 hitboxes/drag) and ensure consistent feel.
 */
public final class LegacyArrowSim {
    private final JavaPlugin plugin;

    public LegacyArrowSim(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Shoot a simulated legacy arrow using 1.8-ish physics.
     *
     * @param shooter Player who shot
     * @param start   Start location (typically eye location)
     * @param dir     Direction vector (normalized inside)
     * @param speed   Initial speed (suggested max ~3.0 at full charge)
     */
    public void shootLegacyArrow(Player shooter, Location start, Vector dir, double speed) {
        if (shooter == null || start == null || start.getWorld() == null) return;

        Vector vel = dir.clone().normalize().multiply(speed);
        World world = start.getWorld();

        final double drag = 0.99;     // 1.8-style drag approximation
        final double gravity = 0.05;  // 1.8-style gravity per tick
        final int maxTicks = 80;      // lifetime ~4 seconds
        final double hitboxRadius = 0.3; // collision radius for entities

        final Location pos = start.clone();
        final UUID shooterId = shooter.getUniqueId();

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > maxTicks) { 
                    cancel(); 
                    return; 
                }

                Player s = Bukkit.getPlayer(shooterId);
                if (s == null || !s.isOnline()) { 
                    cancel(); 
                    return; 
                }

                Location from = pos.clone();
                pos.add(vel);

                // Simple raycast: check blocks and entities between from and pos
                if (checkBlockCollision(from, pos, world)) {
                    playHitSound(pos, world);
                    cancel();
                    return;
                }

                if (checkEntityCollision(from, pos, shooterId, s, hitboxRadius, world)) {
                    cancel();
                    return;
                }

                // Play crit effect (use dust particles available in older versions)
                try {
                    world.playEffect(pos, org.bukkit.Effect.CRIT, 0);
                } catch (Throwable ignored) {}

                // Apply legacy physics
                vel.multiply(drag);
                vel.setY(vel.getY() - gravity);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Check if arrow hit a block during this tick.
     */
    private boolean checkBlockCollision(Location from, Location to, World world) {
        // Simple line-of-sight block check
        double distance = from.distance(to);
        if (distance == 0) return false;

        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        Location check = from.clone();
        
        for (double i = 0; i <= distance; i += 0.3) {
            check.add(direction.clone().multiply(0.3));
            Block block = check.getBlock();
            if (block != null && block.getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if arrow hit an entity during this tick.
     */
    private boolean checkEntityCollision(Location from, Location to, UUID shooterId, Player shooter, double radius, World world) {
        double distance = from.distance(to);
        if (distance == 0) return false;

        // Get all entities in the world and check proximity
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.getUniqueId().equals(shooterId)) continue; // Skip shooter
            
            Location entityLoc = entity.getLocation();
            double distToLine = pointToLineDistance(entityLoc, from, to);
            
            // If entity is within hit radius of the arrow path
            if (distToLine <= radius) {
                LivingEntity living = (LivingEntity) entity;
                living.damage(6.0, shooter); // 1.8 bow body shot equivalent
                playHitSound(entityLoc, world);
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate distance from a point to a line segment.
     */
    private double pointToLineDistance(Location point, Location lineStart, Location lineEnd) {
        Vector p = point.toVector();
        Vector a = lineStart.toVector();
        Vector b = lineEnd.toVector();

        Vector ap = p.subtract(a);
        Vector ab = b.clone().subtract(a);

        if (ab.lengthSquared() == 0) {
            return p.distance(a);
        }

        double t = ap.dot(ab) / ab.lengthSquared();
        t = Math.max(0, Math.min(1, t));

        Vector closest = a.clone().add(ab.clone().multiply(t));
        return p.distance(closest);
    }

    private void playHitSound(Location loc, World world) {
        try {
            world.playSound(loc, org.bukkit.Sound.ARROW_HIT, 1f, 1f);
        } catch (Throwable ignored) {}
    }
}

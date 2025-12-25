package com.mmhq.game.arena.special;

import com.mmhq.game.utils.BukkitHelper;
import com.mmhq.game.utils.VersionUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles sword throwing mechanic for the murderer role.
 */
public class SwordThrowHandler implements Listener {
    private final JavaPlugin plugin;
    private final SwordSkinManager swordSkinManager;
    private final Map<UUID, Long> swordCooldowns = new HashMap<>();
    private final Map<UUID, Integer> chargeTasks = new HashMap<>();
    // New runtime task trackers (charge/cooldown/hand particles)
    private final Map<UUID, BukkitTask> chargeRunnables = new HashMap<>();
    private final Map<UUID, BukkitTask> cooldownRunnables = new HashMap<>();
    private final Map<UUID, BukkitTask> handParticleRunnables = new HashMap<>();
    // Track recent glass cracks (block location -> last crack time in ms)
    private final Map<String, Long> recentGlass = new HashMap<>();

    private static final int CHARGE_TICKS = 10;      // 0.5s
    private static final double COOLDOWN_SECONDS = 4.5; // 4.5s
    private static final long GLASS_CRACK_GATE_MS = 150L; // 150ms spam prevention

    public SwordThrowHandler(JavaPlugin plugin, SwordSkinManager swordSkinManager) {
        this.plugin = plugin;
        this.swordSkinManager = swordSkinManager;
    }

    

    @EventHandler
    public void onSwordThrow(PlayerInteractEvent event) {
        if (event.getAction().name().contains("LEFT_CLICK") || event.getAction().name().equals("PHYSICAL")) {
            return;
        }

        Player attacker = event.getPlayer();
        if (!isMurderer(attacker)) return;
        if (!isHoldingKnife(attacker)) return;

        // Cooldown gate
        long now = System.currentTimeMillis();
        long lastThrow = swordCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        if ((now - lastThrow) < (COOLDOWN_SECONDS * 1000L)) return;

        // Already charging? ignore
        if (chargeRunnables.containsKey(attacker.getUniqueId())) return;

        startCharge(attacker);
    }

    /**
     * Handle item held change: start hand particles if holding knife, stop otherwise.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (isMurderer(p) && isHoldingKnife(p)) {
                startHandParticles(p);
            } else {
                stopHandParticles(p);
            }
        }, 1L);
    }

    /**
     * Handle player quit: clean up all tasks and maps.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        // Cancel and remove all running tasks
        BukkitTask chargeTask = chargeRunnables.remove(uuid);
        if (chargeTask != null) chargeTask.cancel();

        BukkitTask cooldownTask = cooldownRunnables.remove(uuid);
        if (cooldownTask != null) cooldownTask.cancel();

        // Stop hand particles
        stopHandParticles(p);

        // Clean up cooldown tracking
        swordCooldowns.remove(uuid);
        chargeTasks.remove(uuid);
    }

    private boolean isHoldingKnife(Player p) {
        ItemStack hand = VersionUtils.getItemInHand(p);
        return hand != null && hand.getType() == Material.IRON_SWORD;
    }

    // TODO: Hook into your real role system
    private boolean isMurderer(Player p) {
        return true;
    }

    private boolean isGlass(Material m) {
        if (m == null) return false;
        String n = m.name().toUpperCase();
        return n.contains("GLASS");
    }

    private void actionBar(Player p, String msg) {
        VersionUtils.sendActionBar(p, msg);
    }

    private void playHat(Player p, float pitch) {
        playSoundAny(p, pitch, 2.0f,
                "BLOCK_NOTE_BLOCK_HAT",   // 1.13+
                "BLOCK_NOTE_HAT",         // some forks
                "NOTE_STICKS",            // 1.8
                "NOTE_HAT",               // 1.8 alt
                "CLICK",                  // 1.8
                "UI_BUTTON_CLICK"         // modern click
        );
    }

    private void playDragonWing(Player p) {
        playSoundAny(p, 1.2f, 1.0f, "ENTITY_ENDER_DRAGON_FLAP", "ENDERDRAGON_WINGS");
    }

    private void playSoundAny(Player p, float pitch, float vol, String... names) {
        for (String s : names) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf(s), vol, pitch);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private void startCharge(Player p) {
        UUID id = p.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !isMurderer(p) || !isHoldingKnife(p)) {
                    cleanupCharge(id);
                    return;
                }

                double prog = (double) t / CHARGE_TICKS;
                int filled = (int) Math.round(prog * 10.0);
                String bar = bar10(filled);
                double secondsLeft = Math.max(0.0, 0.5 - (t / 20.0));
                actionBar(p, "&6CHARGING &7[ " + bar + " &7] &6" + String.format("%.2f", secondsLeft) + "s");

                if (t == 0) playHat(p, 0.7f);
                if (t == 5) playHat(p, 1.0f);
                if (t == 9) playHat(p, 1.3f);

                if (t >= CHARGE_TICKS) {
                    cleanupCharge(id);
                    playDragonWing(p);
                    swordCooldowns.put(id, System.currentTimeMillis());
                    actionBar(p, "&6THROWING &7[ &a■ ■ ■ ■ ■ ■ ■ ■ &c■ ■ &7] &6" + String.format("%.1f", COOLDOWN_SECONDS) + "s");
                    createFlyingSword(p);
                    startCooldownBar(p, COOLDOWN_SECONDS);
                    return;
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        chargeRunnables.put(id, task);
    }

    private void cleanupCharge(UUID id) {
        BukkitTask t = chargeRunnables.remove(id);
        if (t != null) t.cancel();
    }

    private String bar10(int filled) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? "&a■ " : "&c■ ");
        }
        return sb.toString().trim();
    }

    private void startCooldownBar(Player p, double secondsTotal) {
        UUID id = p.getUniqueId();
        BukkitTask old = cooldownRunnables.remove(id);
        if (old != null) old.cancel();

        BukkitTask task = new BukkitRunnable() {
            double remaining = secondsTotal;

            @Override
            public void run() {
                if (!p.isOnline()) { cancel(); return; }
                int green = Math.max(0, (int) Math.round((remaining / secondsTotal) * 10.0));
                String bar = bar10(green);
                
                // Show only decimals without the "0" (e.g., ".5" instead of "0.5")
                String timeStr = remaining <= 0.0 ? "0" : String.format("%.1f", remaining).replaceFirst("^0", "");
                
                actionBar(p, "&6THROWING &7[ " + bar + " &7] &6" + timeStr + "s");
                remaining -= 0.05; // Tick every 1/20th second (50ms)
                if (remaining <= 0.0) {
                    // Play pickup sound when cooldown ends
                    playSoundAny(p, 1.0f, 1.0f, "ENTITY_ITEM_PICKUP", "ITEM_PICKUP");
                    cancel();
                    cooldownRunnables.remove(id);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick for smooth decimal countdown

        cooldownRunnables.put(id, task);
    }

    // Per-player hand particle task
    public void startHandParticles(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask old = handParticleRunnables.remove(id);
        if (old != null) old.cancel();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !isMurderer(p) || !isHoldingKnife(p)) {
                    cancel();
                    return;
                }

                Location eye = p.getEyeLocation();
                Vector f = eye.getDirection().normalize();
                Vector worldUp = new Vector(0, 1, 0);

                Vector right = f.clone().crossProduct(worldUp);
                if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
                else right.normalize();

                Vector up = right.clone().crossProduct(f).normalize();

                // Right-hand-ish point near screen
                Location hand = eye.clone()
                        .add(f.clone().multiply(0.35))
                        .add(right.clone().multiply(0.20))
                        .add(up.clone().multiply(-0.35));

                VersionUtils.spawnRedstoneDust(hand, 2); // 2 particles, once per second
            }
        }.runTaskTimer(plugin, 0L, 20L);

        handParticleRunnables.put(id, task);
    }

    public void stopHandParticles(Player p) {
        BukkitTask t = handParticleRunnables.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    private void createFlyingSword(Player attacker) {
        Location loc = attacker.getLocation();
        Location eye = attacker.getEyeLocation();
        Location body = attacker.getLocation();

        // Reset sprint to walk (momentum interrupt) without affecting movement velocity
        attacker.setSprinting(false);

        final Vector forward = eye.getDirection().normalize();
        Vector worldUp = new Vector(0, 1, 0);

        // Candidate right (screen-right)
        Vector rightTemp = worldUp.clone().crossProduct(forward);
        if (rightTemp.lengthSquared() < 1e-6) rightTemp = new Vector(1, 0, 0);
        else rightTemp.normalize();

        // Force right to match player's yaw-right (prevents sign flips)
        double yawRad = Math.toRadians(body.getYaw());
        Vector yawRight = new Vector(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        if (rightTemp.dot(yawRight) < 0) rightTemp.multiply(-1);
        final Vector right = rightTemp;

        // Now compute up
        Vector up = forward.clone().crossProduct(right).normalize();
        if (up.dot(new Vector(0, 1, 0)) < 0) up.multiply(-1); // keep screen-up aligned with world up

        // Sanity log for basis vectors
        plugin.getLogger().info("right=" + right + " up=" + up);

        double speed = plugin.getConfig().getDouble("Sword.Speed", 0.65);
        Vector vec = forward.clone().multiply(speed);

        // Where you want the SWORD to visually start (still tune these)
        double itemForward = 0.30;
        double itemRight   = 0.00;
        double itemUp      = 0.00;

        Location origin = eye.clone()
                .add(forward.clone().multiply(itemForward))
                .add(right.clone().multiply(itemRight))
                .add(up.clone().multiply(itemUp));

        // These are NOT "tuning", they're model geometry compensation
        double handSide   = -0.30; // side offset of the stand center to make its hand line up
        double handHeight = 0.80;  // vertical offset from stand feet -> hand (world Y)

        // Base stand position (hand-aligned) off the origin ray
        Location standPosBase = origin.clone()
            .subtract(right.clone().multiply(handSide))         // left
            .subtract(new Vector(0, 1, 0).multiply(handHeight)); // DOWN in world Y

        // Hit detection uses the origin directly
        Location hitPos = origin.clone();

        // Final calibrated visual offsets (from in-game calibration)
        final double VIS_RIGHT = -1.100; // negative = left, positive = right
        final double VIS_UP    = -0.610; // negative = down, positive = up (along camera up vector)
        final double VIS_FWD   = 0.000;  // forward/back offset

        // Combine base stand offset (hand alignment) with visual nudges
        final Vector baseOffset = standPosBase.toVector().subtract(origin.toVector());
        Location visualPos = hitPos.clone()
            .add(baseOffset)
            .add(right.clone().multiply(VIS_RIGHT))
            .add(up.clone().multiply(VIS_UP))
            .add(forward.clone().multiply(VIS_FWD));

        plugin.getLogger().info("DY stand-origin (base) = " + String.format("%.3f", (standPosBase.getY() - origin.getY())));
        plugin.getLogger().info("DX stand-origin along right (base) = " + String.format("%.3f", (standPosBase.clone().subtract(origin).toVector().dot(right))));
        plugin.getLogger().info("Visual offsets: right=%.3f, up=%.3f, fwd=%.3f".formatted(VIS_RIGHT, VIS_UP, VIS_FWD));
        plugin.getLogger().info("Origin (Sword): x=" + String.format("%.3f", origin.getX()) + 
            ", y=" + String.format("%.3f", origin.getY()) + 
            ", z=" + String.format("%.3f", origin.getZ()));
        plugin.getLogger().info("Visual Spawn: x=" + String.format("%.3f", visualPos.getX()) + 
            ", y=" + String.format("%.3f", visualPos.getY()) + 
            ", z=" + String.format("%.3f", visualPos.getZ()));

        ArmorStand stand = (ArmorStand) attacker.getWorld().spawnEntity(visualPos, EntityType.ARMOR_STAND);
        stand.setVisible(false);

        try {
            stand.getClass().getMethod("setInvulnerable", Boolean.TYPE).invoke(stand, true);
            stand.getClass().getMethod("setSilent", Boolean.TYPE).invoke(stand, true);
        } catch (Throwable e) {}

        VersionUtils.setItemInHand(stand, swordSkinManager.getMurdererSword(attacker));
        stand.setRightArmPose(new EulerAngle(Math.toRadians(350.0), Math.toRadians(loc.getPitch() * -1.0), Math.toRadians(90.0)));
        VersionUtils.setCollidable(stand, false);

        try {
            stand.setGravity(false);
            stand.setRemoveWhenFarAway(true);
        } catch (Throwable e) {}

        try {
            stand.setMarker(true);
        } catch (Throwable e) {}

        // Optional visual consistency
        try { stand.getClass().getMethod("setArms", Boolean.TYPE).invoke(stand, true); } catch (Throwable ignored) {}
        try { stand.getClass().getMethod("setBasePlate", Boolean.TYPE).invoke(stand, false); } catch (Throwable ignored) {}
        try { stand.getClass().getMethod("setSmall", Boolean.TYPE).invoke(stand, false); } catch (Throwable ignored) {}

        int maxRange = Math.min(plugin.getConfig().getInt("Sword.Fly.Range", 20), 200);
        double maxHitRange = plugin.getConfig().getDouble("Sword.Fly.Radius", 0.5);
        int maxTicks = plugin.getConfig().getInt("Sword.Fly.MaxTicks", 300); // 15s @ 20tps
        World world = attacker.getWorld();

        // Mutable tracking for movement (hit ray stays canonical)
        Location hitLocTick = hitPos.clone();

        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                ticks++;

                // Move hit location by velocity each tick
                hitLocTick.add(vec.clone());

                // Visual-only position (stand) follows the hit ray with small nudges
                Location visualLocTick = hitLocTick.clone()
                    .add(baseOffset)
                    .add(right.clone().multiply(VIS_RIGHT))
                    .add(up.clone().multiply(VIS_UP))
                    .add(forward.clone().multiply(VIS_FWD));

                VersionUtils.teleport(stand, visualLocTick);

                // Debug every tick
                plugin.getLogger().info("[Tick " + ticks + "] Visual: x=" + 
                    String.format("%.2f", visualLocTick.getX()) + ", y=" + 
                    String.format("%.2f", visualLocTick.getY()) + ", z=" + 
                    String.format("%.2f", visualLocTick.getZ()) + 
                    " | Hit: x=" + String.format("%.2f", hitLocTick.getX()) + ", y=" + 
                    String.format("%.2f", hitLocTick.getY()) + ", z=" + 
                    String.format("%.2f", hitLocTick.getZ()));

                // Check hits against hit position
                for (Entity entity : world.getNearbyEntities(hitLocTick, maxHitRange, maxHitRange, maxHitRange)) {
                    if (!(entity instanceof Player)) continue;
                    Player victim = (Player) entity;
                    if (victim.equals(attacker)) continue;
                    
                    plugin.getLogger().info("[Tick " + ticks + "] HIT PLAYER: " + victim.getName());
                    victim.damage(20.0);
                }

                // Check block collision at hit position
                org.bukkit.block.Block b = hitLocTick.getBlock();
                Material blockType = b.getType();
                String blockName = blockType.name().toUpperCase();

                if (isGlass(blockType)) {
                    // Glass or pane: play sound + crack overlay (with spam prevention) + continue
                    if (shouldCrack(b.getLocation())) {
                        try {
                            world.playSound(hitLocTick, Sound.valueOf("BLOCK_GLASS_BREAK"), 2.0f, 1.2f);
                        } catch (Throwable e) {
                            try {
                                world.playSound(hitLocTick, Sound.valueOf("GLASS"), 2.0f, 1.2f);
                            } catch (Throwable ex) {}
                        }
                        // Show crack animation (stage 7 = heavy crack, auto-clear after 8 ticks)
                        VersionUtils.showBlockCrack(world, b.getLocation(), 7, 8);
                    }
                } else if (blockType.isSolid()) {
                    // Solid block (not glass): stop
                    plugin.getLogger().info("[Tick " + ticks + "] HIT BLOCK: " + blockName);
                    cancel();
                    stand.remove();
                    return;
                }

                if (ticks >= maxTicks) {
                    plugin.getLogger().info("[Tick " + ticks + "] MAX LIFETIME HIT: " + maxTicks);
                    cancel();
                    stand.remove();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Check if a glass block crack should be shown (prevents spam).
     * Returns true if >= 150ms have passed since the last crack at this location.
     */
    private boolean shouldCrack(Location loc) {
        String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        long now = System.currentTimeMillis();
        long lastCrack = recentGlass.getOrDefault(key, 0L);

        if ((now - lastCrack) >= GLASS_CRACK_GATE_MS) {
            recentGlass.put(key, now);
            return true;
        }
        return false;
    }
}

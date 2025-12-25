package com.mmhq.game.arena.features;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ancient Tomb map feature: Kali vendor NPC.
 * Players can interact with Kali to spend gold on rewards (stub for now).
 * Armor stands can be broken by punching or walking through them.
 */
public final class AncientTombFeature implements MapFeature, Listener {
    private final JavaPlugin plugin;
    private final World world;
    private ArmorStand kaliNpc;
    private static final String KALI_LOCATION = "Kali location TBD"; // TODO: confirm coords
    private static final boolean DEBUG = true;

    public AncientTombFeature(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public void init() {
        if (world == null) {
            plugin.getLogger().warning("[AncientTomb] World not found");
            debugLog("init() failed: world is null");
            return;
        }
        
        debugLog("init() starting - world: " + world.getName());
        spawnKali();
        plugin.getLogger().info("[AncientTomb] Feature initialized - Kali spawned");
        debugLog("init() complete");
    }

    @Override
    public void start() {
        debugLog("start() called - registering event listeners");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[AncientTomb] Feature STARTED - vendors active, armor stands breakable");
        debugLog("start() complete");
    }

    @Override
    public void stop() {
        debugLog("stop() called");
        plugin.getLogger().info("[AncientTomb] Feature STOPPED");
    }

    @Override
    public void cleanup() {
        debugLog("cleanup() starting");
        if (kaliNpc != null) {
            debugLog("cleanup() removing Kali NPC");
            kaliNpc.remove();
            kaliNpc = null;
        }
        plugin.getLogger().info("[AncientTomb] Feature CLEANUP complete");
        debugLog("cleanup() complete");
    }

    @Override
    public String getName() {
        return "AncientTomb";
    }

    private void spawnKali() {
        // TODO: Replace with actual Kali NPC location from config
        Location kaliLoc = new Location(world, 0.5, 105, 20.5);
        debugLog("spawnKali() - spawning at " + kaliLoc);
        
        kaliNpc = (ArmorStand) world.spawnEntity(kaliLoc, org.bukkit.entity.EntityType.ARMOR_STAND);
        kaliNpc.setCustomName("§c[Kali Vendor]");
        kaliNpc.setCustomNameVisible(true);
        kaliNpc.setArms(true);
        kaliNpc.setVisible(false);
        debugLog("spawnKali() - armor stand created with UUID: " + kaliNpc.getUniqueId());
        
        plugin.getLogger().info("[AncientTomb] Kali NPC spawned at " + kaliLoc + " (UUID: " + kaliNpc.getUniqueId() + ")");
        debugLog("spawnKali() complete");
    }
    
    private void debugLog(String msg) {
        if (DEBUG) plugin.getLogger().info("[DEBUG-AncientTomb] " + msg);
    }

    /**
     * Handle player punching armor stands or walking through them.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof ArmorStand)) {
            return;
        }
        
        Player damager = (Player) event.getDamager();
        ArmorStand stand = (ArmorStand) event.getEntity();
        
        // Check if this is a special armor stand (not Kali)
        if (stand.equals(kaliNpc)) {
            debugLog("onEntityDamage() - Kali protection: cancel punch by " + damager.getName());
            event.setCancelled(true);
            return;
        }
        
        // Breakable armor stands
        if (stand.getCustomName() != null && stand.getCustomName().contains("Armor")) {
            plugin.getLogger().info("[AncientTomb] Armor stand BROKEN by " + damager.getName() + " at " + stand.getLocation());
            debugLog("onEntityDamage() - armor stand destroyed by punch");
            stand.remove();
        }
    }

    /**
     * Detect if player walks through armor stands (proximity check).
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        
        for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
            if (stand.equals(kaliNpc)) continue;
            
            // 1-block proximity = walking through
            if (loc.distanceSquared(stand.getLocation()) < 2.0) {
                if (stand.getCustomName() != null && stand.getCustomName().contains("Armor")) {
                    plugin.getLogger().info("[AncientTomb] Armor stand DESTROYED by walking through (player: " + player.getName() + ")");
                    debugLog("onPlayerMove() - armor stand destroyed by contact at " + stand.getLocation());
                    stand.remove();
                }
            }
        }
    }
}

package com.mmhq.game.arena.features;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hypixel World map - currently no special features stubbed.
 * Can add parkour mechanics, custom kits, or other features as needed.
 */
public final class HypixelWorldFeature implements MapFeature {
    private final JavaPlugin plugin;
    private final World world;
    private static final boolean DEBUG = true;

    public HypixelWorldFeature(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public void init() {
        if (world == null) {
            plugin.getLogger().warning("[HypixelWorld] World not found");
            debugLog("init() failed: world is null");
            return;
        }
        
        debugLog("init() starting - world: " + world.getName());
        plugin.getLogger().info("[HypixelWorld] Feature initialized");
        debugLog("init() complete");
    }

    @Override
    public void start() {
        debugLog("start() called");
        plugin.getLogger().info("[HypixelWorld] Feature STARTED");
    }

    @Override
    public void stop() {
        debugLog("stop() called");
        plugin.getLogger().info("[HypixelWorld] Feature STOPPED");
    }

    @Override
    public void cleanup() {
        debugLog("cleanup() called");
        plugin.getLogger().info("[HypixelWorld] Feature CLEANUP complete");
    }

    @Override
    public String getName() {
        return "HypixelWorld";
    }
    
    private void debugLog(String msg) {
        if (DEBUG) plugin.getLogger().info("[DEBUG-HypixelWorld] " + msg);
    }
}

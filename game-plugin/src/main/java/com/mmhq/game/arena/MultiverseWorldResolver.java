package com.mmhq.game.arena;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Helper to load worlds via Multiverse-Core when they're unloaded.
 * If MV is not installed, falls back to standard Bukkit world lookup.
 * Note: Multiverse-Core is a soft dependency, so we handle ClassNotFoundException gracefully.
 */
public final class MultiverseWorldResolver {
    private MultiverseWorldResolver() {}

    public static World getOrLoadWorld(JavaPlugin plugin, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w != null) return w;

        // Try Multiverse-Core if available
        org.bukkit.plugin.Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null) {
            plugin.getLogger().warning("[Arena] Multiverse-Core not installed; cannot load " + worldName);
            return null;
        }

        try {
            // Attempt to use Multiverse if the classes are available
            // Note: This uses reflection-style access to avoid hard dependency
            Object worldMgr = mv.getClass().getMethod("getMVWorldManager").invoke(mv);
            Boolean isMVWorld = (Boolean) worldMgr.getClass().getMethod("isMVWorld", String.class).invoke(worldMgr, worldName);
            
            if (!isMVWorld) {
                plugin.getLogger().warning("[Arena] MV world not registered: " + worldName);
                return null;
            }

            worldMgr.getClass().getMethod("loadWorld", String.class).invoke(worldMgr, worldName);
            w = Bukkit.getWorld(worldName);
            
            if (w != null) {
                plugin.getLogger().info("[Arena] World loaded via Multiverse: " + worldName);
            }
            return w;
        } catch (Exception e) {
            plugin.getLogger().warning("[Arena] Failed to load world via Multiverse: " + e.getMessage());
            return null;
        }
    }
}

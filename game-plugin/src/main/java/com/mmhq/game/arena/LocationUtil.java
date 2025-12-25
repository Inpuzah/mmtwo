package com.mmhq.game.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Utility class for location and teleport operations.
 */
public final class LocationUtil {
    
    private LocationUtil() {
        // Utility class
    }

    /**
     * Teleport all players in a world to a location and set their game mode.
     */
    public static void tpAllPlayers(World world, Location loc, GameMode mode) {
        if (world == null || loc == null) return;
        
        for (Player p : world.getPlayers()) {
            p.setGameMode(mode);
            p.teleport(loc);
        }
    }

    /**
     * Teleport a single player to a location safely.
     * Ensures the location has a valid world before teleporting.
     */
    public static boolean safeTeleport(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        return player.teleport(loc);
    }
}

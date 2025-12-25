package com.mmhq.game.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Helper utilities for Bukkit-specific operations.
 */
public final class BukkitHelper {

    /**
     * Rotate a vector around the Y axis by a given angle (in degrees).
     * @param vector The vector to rotate
     * @param yaw The yaw angle in degrees
     * @return A new rotated vector
     */
    public static Vector rotateAroundAxisY(Vector vector, double yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double x = vector.getX() * cos + vector.getZ() * sin;
        double z = vector.getX() * -sin + vector.getZ() * cos;

        return new Vector(x, vector.getY(), z);
    }

    /**
     * Rotate a vector around the X axis by a given angle (in degrees).
     * @param vector The vector to rotate
     * @param pitch The pitch angle in degrees
     * @return A new rotated vector
     */
    public static Vector rotateAroundAxisX(Vector vector, double pitch) {
        double radians = Math.toRadians(pitch);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double y = vector.getY() * cos - vector.getZ() * sin;
        double z = vector.getY() * sin + vector.getZ() * cos;

        return new Vector(vector.getX(), y, z);
    }

    /**
     * Send an action bar message to a player.
     * @param player The player
     * @param message The message
     */
    public static void sendActionBar(Player player, String message) {
        // 1.8.8 doesn't have action bar, send as regular message
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Apply action bar cooldown display.
     * @param player The player
     * @param seconds The cooldown seconds
     */
    public static void applyActionBarCooldown(Player player, int seconds) {
        sendActionBar(player, "§cCooldown: §f" + seconds + "s");
    }
}

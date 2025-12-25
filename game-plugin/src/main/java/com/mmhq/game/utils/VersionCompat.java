package com.mmhq.game.utils;

import org.bukkit.Bukkit;

/**
 * Version compatibility utilities for supporting multiple Minecraft versions.
 * This plugin supports Paper/Spigot 1.8.9 - 1.21 (via ViaVersion).
 *
 * Supported versions:
 * - 1.8.9 (base version)
 * - 1.12.2 - 1.20 (direct support on Paper)
 * - 1.21 (via ViaVersion for 1.8.9 servers)
 */
public final class VersionCompat {
    private static final String SERVER_VERSION;
    private static final String BUKKIT_VERSION;
    private static final int PROTOCOL_VERSION;

    static {
        BUKKIT_VERSION = Bukkit.getVersion();
        SERVER_VERSION = getServerVersion();
        PROTOCOL_VERSION = parseProtocolVersion();
    }

    /**
     * Extract server version from Bukkit version string.
     * Format: "CraftBukkit version git-xxx (MC: 1.8.9)"
     * @return Server version string (e.g., "1.8.9")
     */
    private static String getServerVersion() {
        String version = Bukkit.getVersion();
        int start = version.lastIndexOf("MC: ") + 4;
        int end = version.lastIndexOf(")");
        if (start > 3 && end > start) {
            return version.substring(start, end);
        }
        return "Unknown";
    }

    /**
     * Parse protocol version from server version.
     * Returns approximate protocol version for compatibility checks.
     */
    private static int parseProtocolVersion() {
        try {
            String[] parts = SERVER_VERSION.split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                // Protocol version mapping (approximate)
                return 47 + (major - 1) * 2 + (minor > 0 ? 1 : 0);
            }
        } catch (Exception e) {
            // Fallback to 1.8.9 protocol
        }
        return 47; // 1.8.9 protocol version
    }

    /**
     * Get the server version string.
     * @return Server version (e.g., "1.8.9")
     */
    public static String getVersion() {
        return SERVER_VERSION;
    }

    /**
     * Check if server is running 1.8.x
     */
    public static boolean is1_8() {
        return SERVER_VERSION.startsWith("1.8");
    }

    /**
     * Check if server is running 1.9 or later
     */
    public static boolean is1_9OrLater() {
        return !is1_8();
    }

    /**
     * Check if server is running 1.13 or later (has new colors)
     */
    public static boolean is1_13OrLater() {
        try {
            String[] parts = SERVER_VERSION.split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);
                return minor >= 13;
            }
        } catch (Exception e) {
            // Fallback
        }
        return false;
    }

    /**
     * Check if ViaVersion is available for 1.21 client support
     */
    public static boolean isViaVersionAvailable() {
        try {
            Class.forName("com.viaversion.viaversion.api.Via");
            return Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get protocol version for version checks
     */
    public static int getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    /**
     * Get full Bukkit version string
     */
    public static String getBukkitVersion() {
        return BUKKIT_VERSION;
    }

    /**
     * Check if server supports NMS (New Mapping System)
     * Used for reflection-based features
     */
    public static boolean supportsNMS() {
        return !is1_20_5OrLater();
    }

    /**
     * Check if server is running 1.20.5 or later (major NMS changes)
     */
    public static boolean is1_20_5OrLater() {
        try {
            String[] parts = SERVER_VERSION.split("\\.");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                
                if (major > 1) return true;
                if (major == 1 && minor > 20) return true;
                if (major == 1 && minor == 20 && patch >= 5) return true;
            }
        } catch (Exception e) {
            // Fallback
        }
        return false;
    }

    /**
     * Print version compatibility info to console
     */
    public static void logVersionInfo(org.bukkit.plugin.java.JavaPlugin plugin) {
        plugin.getLogger().info("=".repeat(60));
        plugin.getLogger().info("MMHQ Murder Mystery - Version Compatibility Info");
        plugin.getLogger().info("=".repeat(60));
        plugin.getLogger().info("Server Version: " + getVersion());
        plugin.getLogger().info("Bukkit Version: " + getBukkitVersion());
        plugin.getLogger().info("Protocol Version: " + getProtocolVersion());
        plugin.getLogger().info("ViaVersion Available: " + (isViaVersionAvailable() ? "YES (1.21 support)" : "NO"));
        plugin.getLogger().info("NMS Support: " + (supportsNMS() ? "YES" : "NO"));
        plugin.getLogger().info("=".repeat(60));
    }
}

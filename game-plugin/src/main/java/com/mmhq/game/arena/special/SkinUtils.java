package com.mmhq.game.arena.special;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utilities for copying player skins to GameProfile objects.
 * Supports 1.8+ with reflection-based access to GameProfile textures.
 */
public final class SkinUtils {

    /**
     * Copy skin textures from a real player to a GameProfile (for NPC rendering).
     * Uses reflection to work across versions.
     * 
     * @param victim The player whose skin to copy
     * @param targetProfile The GameProfile to apply skin to
     * @return true if successful, false otherwise
     */
    public static boolean copySkin(Player victim, Object targetProfile) {
        try {
            // Try to get the victim's GameProfile via reflection
            Object victimHandle = getPlayerHandle(victim);
            if (victimHandle == null) {
                Bukkit.getLogger().warning("[SkinUtils] Failed to get victim handle for " + victim.getName());
                return false;
            }

            Object victimProfile = victimHandle.getClass().getField("profile").get(victimHandle);
            if (victimProfile == null) {
                Bukkit.getLogger().warning("[SkinUtils] Victim has no profile: " + victim.getName());
                return false;
            }

            // Get textures property from victim profile
            Class<?> propertyClz = Class.forName("com.mojang.authlib.properties.Property");
            Object victimProperties = victimProfile.getClass().getMethod("getProperties").invoke(victimProfile);
            
            // Copy "textures" property to target
            Object texturesProp = null;
            for (Object prop : (Iterable<?>) victimProperties.getClass().getMethod("get", String.class).invoke(victimProperties, "textures")) {
                texturesProp = prop;
                break;
            }

            if (texturesProp != null) {
                Object targetProperties = targetProfile.getClass().getMethod("getProperties").invoke(targetProfile);
                targetProperties.getClass().getMethod("put", String.class, propertyClz).invoke(targetProperties, "textures", texturesProp);
                Bukkit.getLogger().info("[SkinUtils] Copied skin from " + victim.getName() + " to target profile");
                return true;
            } else {
                Bukkit.getLogger().warning("[SkinUtils] No textures property found for " + victim.getName());
                return false;
            }
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[SkinUtils] Failed to copy skin: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the underlying EntityPlayer / CraftPlayer handle via reflection.
     */
    private static Object getPlayerHandle(Player p) {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.split("\\.")[3];
            Class<?> craftPlayerClz = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            return craftPlayerClz.getMethod("getHandle").invoke(p);
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[SkinUtils] Failed to get player handle: " + e.getMessage());
            return null;
        }
    }
}

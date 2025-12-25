package com.mmhq.game.arena.special;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages corpses using the Corpse plugin API (https://github.com/unldenis/Corpse).
 * Falls back gracefully if the plugin isn't installed.
 */
public final class CorpseManager {
    private final JavaPlugin plugin;
    private boolean corpsePluginAvailable = false;

    // Reflection caches for Corpse API
    private Object corpseApiInstance;
    private Method spawnCorpseMethod;
    private Method removeCorpseMethod;

    private final Map<UUID, CorpseEntry> corpses = new ConcurrentHashMap<>();

    private static final boolean DEBUG = true;

    public CorpseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize Corpse plugin integration. Call from onEnable() AFTER dependencies are loaded.
     */
    public void init() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Corpse") == null) {
                log("[Corpse] Corpse plugin not found - corpses disabled");
                return;
            }

            // Get CorpseAPI.getInstance()
            Class<?> corpseApiClass = Class.forName("com.github.unldenis.corpse.api.CorpseAPI");
            Method getInstance = corpseApiClass.getMethod("getInstance");
            corpseApiInstance = getInstance.invoke(null);

            // Cache methods
            // spawnCorpse(Player owner, Location loc) returns Corpse
            spawnCorpseMethod = corpseApiClass.getMethod("spawnCorpse", Player.class, Location.class);
            
            // removeCorpse(Corpse corpse)
            Class<?> corpseClass = Class.forName("com.github.unldenis.corpse.api.Corpse");
            removeCorpseMethod = corpseApiClass.getMethod("removeCorpse", corpseClass);

            corpsePluginAvailable = true;
            log("[Corpse] Corpse plugin API initialized successfully");

        } catch (ClassNotFoundException e) {
            log("[Corpse] Corpse plugin classes not found: " + e.getMessage());
        } catch (Throwable t) {
            log("[Corpse] Corpse plugin init failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            t.printStackTrace();
        }
    }

    public boolean isAvailable() {
        return corpsePluginAvailable;
    }

    /**
     * Spawn a corpse at victim's death location.
     * @param victim The player who died (skin will be used)
     * @param deathLoc Where to spawn the corpse
     */
    public void spawnCorpse(Player victim, Location deathLoc) {
        spawnCorpse(victim, deathLoc, 20L * 30); // Default 30 second TTL
    }

    /**
     * Spawn a corpse at victim's death location with custom TTL.
     * @param victim The player who died (skin will be used)
     * @param deathLoc Where to spawn the corpse
     * @param ttlTicks How long until the corpse auto-despawns (in ticks)
     */
    public void spawnCorpse(Player victim, Location deathLoc, long ttlTicks) {
        if (!corpsePluginAvailable) {
            log("[Corpse] Cannot spawn - Corpse plugin not available");
            return;
        }
        if (victim == null || deathLoc == null) return;

        UUID victimId = victim.getUniqueId();

        // Remove existing corpse for this victim
        CorpseEntry old = corpses.remove(victimId);
        if (old != null) {
            log("[Corpse] Removing existing corpse for " + victim.getName());
            if (old.ttlTask != null) old.ttlTask.cancel();
            removeCorpseInternal(old.corpseObject);
        }

        try {
            // Spawn via API
            Object corpse = spawnCorpseMethod.invoke(corpseApiInstance, victim, deathLoc);
            
            if (corpse == null) {
                log("[Corpse] spawnCorpse returned null for " + victim.getName());
                return;
            }

            log("[Corpse] Spawned corpse for " + victim.getName() + " at " + fmt(deathLoc));

            // Schedule TTL cleanup
            BukkitTask ttlTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                CorpseEntry entry = corpses.remove(victimId);
                if (entry != null) {
                    log("[Corpse] TTL expired for " + victim.getName());
                    removeCorpseInternal(entry.corpseObject);
                }
            }, ttlTicks);

            corpses.put(victimId, new CorpseEntry(corpse, ttlTask, victim.getName()));

        } catch (Throwable t) {
            plugin.getLogger().severe("[Corpse] spawnCorpse ERROR: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Despawn a corpse for a specific victim.
     */
    public void despawnCorpse(UUID victimId) {
        CorpseEntry entry = corpses.remove(victimId);
        if (entry == null) return;

        if (entry.ttlTask != null) entry.ttlTask.cancel();
        removeCorpseInternal(entry.corpseObject);
        log("[Corpse] Despawned corpse for " + entry.victimName);
    }

    /**
     * Clear all active corpses.
     */
    public void clearAll() {
        for (UUID id : corpses.keySet()) {
            CorpseEntry entry = corpses.remove(id);
            if (entry != null) {
                if (entry.ttlTask != null) entry.ttlTask.cancel();
                removeCorpseInternal(entry.corpseObject);
            }
        }
        log("[Corpse] Cleared all corpses");
    }

    private void removeCorpseInternal(Object corpseObject) {
        if (!corpsePluginAvailable || corpseObject == null) return;
        try {
            removeCorpseMethod.invoke(corpseApiInstance, corpseObject);
        } catch (Throwable t) {
            log("[Corpse] removeCorpse failed: " + t.getMessage());
        }
    }

    // ===== Helpers =====

    private static String fmt(Location l) {
        return String.format("%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
    }

    private static void log(String s) {
        if (DEBUG) Bukkit.getLogger().info(s);
    }

    /**
     * Internal tracking for spawned corpses.
     */
    private static final class CorpseEntry {
        final Object corpseObject;  // The Corpse API object
        final BukkitTask ttlTask;
        final String victimName;

        CorpseEntry(Object corpseObject, BukkitTask ttlTask, String victimName) {
            this.corpseObject = corpseObject;
            this.ttlTask = ttlTask;
            this.victimName = victimName;
        }
    }
}
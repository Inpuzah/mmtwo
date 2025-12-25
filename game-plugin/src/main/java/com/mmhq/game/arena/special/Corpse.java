package com.mmhq.game.arena.special;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single corpse NPC (fake player lying down).
 * Stores all state needed to render and cleanup.
 */
public class Corpse {
    public final UUID victimId;           // The original player's UUID
    public final String victimName;       // Original player name (for logging)
    public final UUID npcUuid;            // Fake NPC player UUID
    public final String npcName;          // Fake NPC name (<=16 chars, unique)
    public final int entityId;            // Entity ID assigned by server
    public final Location deathLoc;       // Where death occurred
    public final Location bedPos;         // Bed anchor for sleeping pose
    public final Set<UUID> viewers;       // Players who have seen this corpse
    public BukkitTask ttlTask;            // TTL countdown task
    public long spawnedAt;                // System.currentTimeMillis() when spawned

    public Corpse(UUID victimId, String victimName, UUID npcUuid, String npcName, int entityId,
                  Location deathLoc, Location bedPos) {
        this.victimId = victimId;
        this.victimName = victimName;
        this.npcUuid = npcUuid;
        this.npcName = npcName;
        this.entityId = entityId;
        this.deathLoc = deathLoc;
        this.bedPos = bedPos;
        this.viewers = new HashSet<>();
        this.spawnedAt = System.currentTimeMillis();
    }

    public long getAgeSeconds() {
        return (System.currentTimeMillis() - spawnedAt) / 1000L;
    }

    public void cancelTTL() {
        if (ttlTask != null) {
            ttlTask.cancel();
            ttlTask = null;
        }
    }
}

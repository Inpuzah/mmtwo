package com.mmhq.game.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Defines a map configuration for the Murder Mystery game.
 * Maps use a templateWorld that gets cloned to an active world at runtime.
 * All spawns are world-agnostic (world is assigned at runtime).
 * 
 * Supports two modes:
 * 1. Legacy mode: world() returns Bukkit.getWorld(templateWorld) 
 * 2. New mode: waitingSpawn(activeWorld) returns spawn with world set to activeWorld
 */
public final class MapDefinition {
    private final String id;
    private final String templateWorld;
    private final Location waitingSpawn;      // world assigned at runtime to active world
    private final List<Location> gameSpawns;  // world assigned at runtime to active world
    private final Location spectatorSpawn;    // world assigned at runtime to active world

    public MapDefinition(String id, String templateWorld, Location waitingSpawn,
                         List<Location> gameSpawns, Location spectatorSpawn) {
        this.id = Objects.requireNonNull(id, "id");
        this.templateWorld = Objects.requireNonNull(templateWorld, "templateWorld");
        this.waitingSpawn = Objects.requireNonNull(waitingSpawn, "waitingSpawn");
        this.gameSpawns = new ArrayList<>(Objects.requireNonNull(gameSpawns, "gameSpawns"));
        this.spectatorSpawn = Objects.requireNonNull(spectatorSpawn, "spectatorSpawn");
    }

    // ===== NEW API =====
    
    public String id() { return id; }
    
    public String templateWorld() { return templateWorld; }

    /**
     * Get the waiting spawn location for the given active world.
     */
    public Location waitingSpawn(World active) { 
        return withWorld(waitingSpawn, active); 
    }

    /**
     * Get all game spawns for the given active world.
     */
    public List<Location> gameSpawns(World active) {
        List<Location> out = new ArrayList<>();
        for (Location l : gameSpawns) {
            out.add(withWorld(l, active));
        }
        return out;
    }

    /**
     * Get the spectator spawn location for the given active world.
     */
    public Location spectatorSpawn(World active) { 
        return withWorld(spectatorSpawn, active); 
    }
    
    // ===== LEGACY API (for backward compatibility) =====
    
    /**
     * Legacy: Get the map name (same as id).
     */
    public String name() { return id; }
    
    /**
     * Legacy: Get the world name (same as templateWorld).
     */
    public String worldName() { return templateWorld; }
    
    /**
     * Legacy: Get the Bukkit world for this map's template.
     * Returns the world by name from Bukkit, or null if not loaded.
     */
    public World world() { 
        return Bukkit.getWorld(templateWorld); 
    }
    
    /**
     * Legacy: Get the waiting spawn with world resolved from Bukkit.
     */
    public Location waitingSpawn() {
        World w = world();
        return withWorld(waitingSpawn, w);
    }
    
    /**
     * Legacy: Get all game spawns with world resolved from Bukkit.
     */
    public List<Location> gameSpawns() {
        World w = world();
        List<Location> out = new ArrayList<>();
        for (Location l : gameSpawns) {
            out.add(withWorld(l, w));
        }
        return out;
    }
    
    /**
     * Legacy: Get spectator spawn with world resolved from Bukkit.
     */
    public Location spectatorSpawn() {
        World w = world();
        return withWorld(spectatorSpawn, w);
    }

    // ===== CONFIG PARSING =====

    /**
     * Parse a MapDefinition from a config section.
     */
    public static MapDefinition fromConfig(String id, ConfigurationSection sec) {
        // Support both new "templateWorld" and legacy "world" key
        String template = sec.getString("templateWorld");
        if (template == null || template.isEmpty()) {
            template = sec.getString("world");
        }
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("templateWorld (or world) missing for map: " + id);
        }

        Location waiting = readLoc(sec.getConfigurationSection("waiting"));
        Location spectator = readLoc(sec.getConfigurationSection("spectator"));
        if (spectator == null) {
            // Default spectator to waiting spawn but higher Y
            spectator = new Location(null, waiting.getX(), waiting.getY() + 20, waiting.getZ(), 
                    waiting.getYaw(), waiting.getPitch());
        }

        List<Location> spawns = new ArrayList<>();
        ConfigurationSection spawnsSec = sec.getConfigurationSection("spawns");
        if (spawnsSec != null) {
            List<String> keys = new ArrayList<>(spawnsSec.getKeys(false));
            // Sort numerically for consistent spawn order
            keys.sort(Comparator.comparingInt(k -> {
                try { return Integer.parseInt(k); } catch (Exception e) { return 9999; }
            }));
            for (String k : keys) {
                ConfigurationSection one = spawnsSec.getConfigurationSection(k);
                if (one != null) {
                    spawns.add(readLoc(one));
                }
            }
        }
        if (spawns.isEmpty()) {
            spawns.add(waiting.clone());
        }

        return new MapDefinition(id, template, waiting, spawns, spectator);
    }

    private static Location readLoc(ConfigurationSection sec) {
        if (sec == null) {
            return new Location(null, 0.5, 70, 0.5, 0f, 0f);
        }
        double x = sec.getDouble("x", 0.5);
        double y = sec.getDouble("y", 70);
        double z = sec.getDouble("z", 0.5);
        float yaw = (float) sec.getDouble("yaw", 0);
        float pitch = (float) sec.getDouble("pitch", 0);
        return new Location(null, x, y, z, yaw, pitch);
    }

    private static Location withWorld(Location base, World w) {
        return new Location(w, base.getX(), base.getY(), base.getZ(), base.getYaw(), base.getPitch());
    }
}

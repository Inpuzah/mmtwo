package com.mmhq.game.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Registry for all available maps.
 * Loads map definitions from config on init; provides lookup by name.
 */
public final class MapRegistry {
    private final Map<String, MapDefinition> maps = new HashMap<>();
    private final JavaPlugin plugin;

    public MapRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadMaps();
    }

    private void loadMaps() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("maps");
        if (sec == null) {
            plugin.getLogger().warning("[MapRegistry] No 'maps' section in config");
            return;
        }

        for (String key : sec.getKeys(false)) {
            ConfigurationSection mapSec = sec.getConfigurationSection(key);
            if (mapSec != null) {
                try {
                    MapDefinition def = MapDefinition.fromConfig(key, mapSec);
                    maps.put(key.toLowerCase(Locale.ROOT), def);
                    plugin.getLogger().info("[MapRegistry] Loaded map: " + key + " (template: " + def.templateWorld() + ")");
                } catch (Exception e) {
                    plugin.getLogger().warning("[MapRegistry] Failed to load map " + key + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get a map by name (case-insensitive).
     */
    public MapDefinition get(String name) {
        if (name == null) return null;
        return maps.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Get all map IDs.
     */
    public Set<String> ids() {
        return new HashSet<>(maps.keySet());
    }

    /**
     * Get all registered maps.
     */
    public Collection<MapDefinition> all() {
        return maps.values();
    }

    /**
     * Reload maps from config.
     */
    public void reload() {
        maps.clear();
        plugin.reloadConfig();
        loadMaps();
        plugin.getLogger().info("[MapRegistry] Maps reloaded");
    }
}

package com.mmhq.game.arena.features;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base interface for map-specific features (NPCs, trains, traps, etc.).
 */
public interface MapFeature {
    /**
     * Initialize the feature. Called when the map is loaded.
     */
    void init();

    /**
     * Start the feature. Called when the game starts.
     */
    void start();

    /**
     * Stop the feature. Called when the game ends or is reset.
     */
    void stop();

    /**
     * Clean up all resources. Called during reset phase.
     */
    void cleanup();

    /**
     * Get the name of this feature for logging.
     */
    String getName();
}

package com.mmhq.game.arena;

import com.mmhq.game.arena.features.AncientTombFeature;
import com.mmhq.game.arena.features.HypixelWorldFeature;
import com.mmhq.game.arena.features.MapFeature;
import com.mmhq.game.arena.features.SubwayFeature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the arena lifecycle and state transitions.
 * Handles:
 * - State transitions (IDLE -> WAITING -> COUNTDOWN -> PREGAME -> IN_PROGRESS -> POST_GAME -> RESET -> IDLE)
 * - Map feature initialization and cleanup
 * - Server communication with lobby
 */
public final class ArenaManager {
    private final JavaPlugin plugin;
    private ArenaState currentState = ArenaState.IDLE;
    private MapDefinition currentMap;
    private final List<MapFeature> activeFeatures = new ArrayList<>();
    private static final boolean DEBUG = true;
    private long stateChangeTime = System.currentTimeMillis();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        debugLog("[ArenaManager] Initialized with state: " + currentState);
    }

    /**
     * Transition to a new arena state.
     */
    public void setState(ArenaState newState) {
        ArenaState oldState = currentState;
        currentState = newState;
        stateChangeTime = System.currentTimeMillis();
        
        debugLog("[ArenaManager] State transition: " + oldState + " -> " + newState);
        plugin.getLogger().info("[Arena] ===== STATE CHANGE: " + oldState + " -> " + newState + " =====");
        
        switch (newState) {
            case IDLE:
                handleIdleState();
                break;
            case WAITING:
                handleWaitingState();
                break;
            case COUNTDOWN:
                handleCountdownState();
                break;
            case PREGAME:
                handlePregameState();
                break;
            case IN_PROGRESS:
                handleInProgressState();
                break;
            case POST_GAME:
                handlePostGameState();
                break;
            case RESETTING:
                handleResetState();
                break;
            case PREPARING:
            case ERROR:
                // New states - handled by ArenaService
                break;
        }
    }

    /**
     * Prepare the arena for a new game with the given map.
     * Ensures the world is loaded via Multiverse if needed.
     */
    public void prepareMap(MapDefinition map) {
        debugLog("[ArenaManager] Preparing map: " + map.name() + " (world: " + map.worldName() + ")");
        
        // Ensure the map's world is loaded
        World world = MultiverseWorldResolver.getOrLoadWorld(plugin, map.worldName());
        if (world == null) {
            plugin.getLogger().warning("[Arena] ✗ Failed to load world for map: " + map.name());
            debugLog("[ArenaManager] FAILED - world could not be loaded");
            return;
        }
        
        plugin.getLogger().info("[Arena] World loaded: " + world.getName());
        
        this.currentMap = map;
        
        // Clean up old features
        if (!activeFeatures.isEmpty()) {
            debugLog("[ArenaManager] Cleaning up " + activeFeatures.size() + " existing features");
            stopAndCleanupFeatures();
        }
        
        // Initialize map-specific features
        initializeMapFeatures(map);
        
        debugLog("[ArenaManager] Map prepared successfully: " + map.name() + " with " + activeFeatures.size() + " features");
        plugin.getLogger().info("[Arena] MAP PREPARED: " + map.name() + " with " + activeFeatures.size() + " active features");
    }

    /**
     * Get the current arena state.
     */
    public ArenaState getState() {
        return currentState;
    }

    /**
     * Get the current map.
     */
    public MapDefinition getCurrentMap() {
        return currentMap;
    }
    
    /**
     * Get arena status for debugging (human-readable).
     */
    public String getStatus() {
        long elapsed = System.currentTimeMillis() - stateChangeTime;
        return String.format("[Arena Status] State: %s | Map: %s | Features: %d | Elapsed: %dms",
                currentState, 
                currentMap != null ? currentMap.name() : "NONE",
                activeFeatures.size(),
                elapsed);
    }

    /**
     * Get arena status in machine-readable format for lobby scoreboard.
     * Format: "STATE:mapName:playerCount:maxPlayers"
     * @param playerCount Current player count from the game
     * @param maxPlayers Max players from preset
     */
    public String getStatusForLobby(int playerCount, int maxPlayers) {
        String mapName = currentMap != null ? currentMap.name() : "";
        return String.format("%s:%s:%d:%d", currentState.name(), mapName, playerCount, maxPlayers);
    }

    // ===== State Handlers =====

    private void handleIdleState() {
        debugLog("[Arena-IDLE] Transitioning to IDLE state");
        stopAndCleanupFeatures();
        debugLog("[Arena-IDLE] All features stopped and cleaned");
    }

    private void handleWaitingState() {
        debugLog("[Arena-WAITING] Transitioning to WAITING state - awaiting minimum players");
        
        // Teleport all online players on the map's world to waiting spawn
        if (currentMap != null) {
            World mapWorld = currentMap.world();
            if (mapWorld != null) {
                org.bukkit.Location waitingSpawn = currentMap.waitingSpawn();
                for (Player player : mapWorld.getPlayers()) {
                    if (player != null && player.isOnline()) {
                        player.teleport(waitingSpawn);
                        debugLog("[Arena-WAITING] Teleported " + player.getName() + " to waiting spawn");
                    }
                }
            }
        }
    }

    private void handleCountdownState() {
        debugLog("[Arena-COUNTDOWN] Transitioning to COUNTDOWN state - starting countdown");
    }

    private void handlePregameState() {
        debugLog("[Arena-PREGAME] Transitioning to PREGAME state - players teleporting to arena");
    }

    private void handleInProgressState() {
        debugLog("[Arena-IN_PROGRESS] Transitioning to IN_PROGRESS state - starting " + activeFeatures.size() + " features");
        
        // Game is live - start all map features
        int started = 0;
        for (MapFeature feature : activeFeatures) {
            try {
                feature.start();
                started++;
                debugLog("[Arena-IN_PROGRESS] Started feature: " + feature.getName());
            } catch (Throwable t) {
                plugin.getLogger().severe("[Arena-IN_PROGRESS] Feature " + feature.getName() + " failed to start: " + t);
                t.printStackTrace();
            }
        }
        plugin.getLogger().info("[Arena] GAME STARTED - " + started + "/" + activeFeatures.size() + " features running");
    }

    private void handlePostGameState() {
        debugLog("[Arena-POST_GAME] Transitioning to POST_GAME state - showing results");
        
        // Game ended - stop features but keep for results
        int stopped = 0;
        for (MapFeature feature : activeFeatures) {
            try {
                feature.stop();
                stopped++;
                debugLog("[Arena-POST_GAME] Stopped feature: " + feature.getName());
            } catch (Throwable t) {
                plugin.getLogger().severe("[Arena-POST_GAME] Feature " + feature.getName() + " failed to stop: " + t);
                t.printStackTrace();
            }
        }
        plugin.getLogger().info("[Arena] GAME ENDED - " + stopped + "/" + activeFeatures.size() + " features stopped");
    }

    private void handleResetState() {
        debugLog("[Arena-RESET] Transitioning to RESET state - full cleanup");
        stopAndCleanupFeatures();
        currentMap = null;
        debugLog("[Arena-RESET] Arena reset complete, ready for next game");
        plugin.getLogger().info("[Arena] ARENA RESET - ready for next game");
    }
    // ===== Feature Management =====

    private void initializeMapFeatures(MapDefinition map) {
        activeFeatures.clear();
        
        World world = map.world();
        if (world == null) {
            plugin.getLogger().warning("[Arena] World not found for map: " + map.name());
            debugLog("[Arena-INIT] ERROR: World does not exist for map '" + map.name() + "'!");
            return;
        }
        
        debugLog("[Arena-INIT] World loaded: " + world.getName() + " (type: " + world.getEnvironment() + ")");
        
        MapFeature feature = null;
        
        switch (map.name()) {
            case "AncientTomb":
                feature = new AncientTombFeature(plugin, world);
                break;
            case "Subway":
                feature = new SubwayFeature(plugin, world);
                break;
            case "HypixelWorld":
                feature = new HypixelWorldFeature(plugin, world);
                break;
            default:
                debugLog("[Arena-INIT] No special features defined for map: " + map.name());
                plugin.getLogger().info("[Arena] No special features for map: " + map.name());
                return;
        }
        
        if (feature != null) {
            try {
                debugLog("[Arena-INIT] Initializing feature: " + feature.getName());
                feature.init();
                activeFeatures.add(feature);
                debugLog("[Arena-INIT] Successfully initialized feature: " + feature.getName());
                plugin.getLogger().info("[Arena] Feature initialized: " + feature.getName());
            } catch (Throwable t) {
                plugin.getLogger().severe("[Arena] Failed to init feature " + map.name() + ": " + t);
                t.printStackTrace();
                debugLog("[Arena-INIT] ERROR initializing " + feature.getName() + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }
    }

    private void stopAndCleanupFeatures() {
        debugLog("[Arena-CLEANUP] Stopping and cleaning up " + activeFeatures.size() + " features");
        
        int cleaned = 0;
        for (MapFeature feature : activeFeatures) {
            try {
                debugLog("[Arena-CLEANUP] Stopping: " + feature.getName());
                feature.stop();
                debugLog("[Arena-CLEANUP] Cleaning: " + feature.getName());
                feature.cleanup();
                cleaned++;
            } catch (Throwable t) {
                plugin.getLogger().severe("[Arena] Feature " + feature.getName() + " cleanup failed: " + t);
                t.printStackTrace();
                debugLog("[Arena-CLEANUP] ERROR cleaning " + feature.getName() + ": " + t.getMessage());
            }
        }
        activeFeatures.clear();
        debugLog("[Arena-CLEANUP] Cleaned up " + cleaned + " features");
    }

    // ===== Debug Logging =====
    
    private void debugLog(String msg) {
        if (DEBUG) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}

package com.mmhq.game;

import com.mmhq.game.arena.ArenaManager;
import com.mmhq.game.arena.ArenaState;
import com.mmhq.game.arena.MapDefinition;
import com.mmhq.game.arena.MapRegistry;
import com.mmhq.game.arena.MurderMysteryGame;
import com.mmhq.game.arena.special.CorpseManager;
import com.mmhq.sharedapi.game.GameState;
import com.mmhq.sharedapi.game.MatchPreset;
import com.mmhq.sharedapi.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameManager implements Listener {
    // ===== Core References =====
    private final JavaPlugin plugin;
    private final MurderMysteryGame currentGame;
    private final MapRegistry mapRegistry;
    private final List<MapDefinition> maps;
    private final CorpseManager corpseManager;
    private final ArenaManager arenaManager;

    // ===== State =====
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public GameManager(JavaPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.corpseManager = new CorpseManager(plugin);

        // Initialize MapRegistry from config
        this.mapRegistry = new MapRegistry(plugin);

        // Load default match preset from config
        MatchPreset defaultPreset = new MatchPreset(
                "default",
                plugin.getConfig().getInt("preset.default.minPlayers", 4),
                plugin.getConfig().getInt("preset.default.maxPlayers", 16),
                Duration.ofSeconds(plugin.getConfig().getInt("preset.default.countdownSeconds", 20)),
                Duration.ofMinutes(plugin.getConfig().getInt("preset.default.roundMinutes", 5)),
                plugin.getConfig().getString("preset.default.map", "world")
        );

        // Keep legacy maps list for backward compatibility
        this.maps = new ArrayList<>(mapRegistry.all());
        this.currentGame = new MurderMysteryGame(plugin, defaultPreset, maps, corpseManager);
    }

    // ===== Public API =====

    public void setNextMap(MapDefinition map) {
        currentGame.setNextMap(map);
        arenaManager.prepareMap(map);
    }

    /**
     * Prepare a game from the lobby: load map via registry, validate, and ready arena for players.
     * Does NOT start the game; players joining will trigger countdown when min threshold reached.
     */
    public void prepareGameWithMap(String mapName) {
        plugin.getLogger().info("[GameManager] Preparing game from lobby with map: " + mapName);
        
        // Look up template map from registry
        MapDefinition template = mapRegistry.get(mapName);
        
        if (template == null) {
            plugin.getLogger().warning("[GameManager] ✗ Map not found in registry: " + mapName);
            return;
        }
        
        // Get the active world (should be mm_active)
        World active = Bukkit.getWorld("mm_active");
        if (active == null) {
            plugin.getLogger().warning("[GameManager] ✗ Active world mm_active is not loaded yet!");
            return;
        }
        
        // Bind template spawns to mm_active
        MapDefinition map = bindToActiveWorld(template, active);
        
        // Reset game state
        plugin.getLogger().info("[GameManager] Clearing queue and resetting game state");
        currentGame.resetToLobby();
        
        // Set the map
        setNextMap(map);
        
        // Set arena to WAITING state (ready for players)
        arenaManager.setState(ArenaState.WAITING);
        
        plugin.getLogger().info("[GameManager] ✓ Game prepared with map: " + mapName + " (bound to mm_active) - READY FOR PLAYERS");
    }

    public List<MapDefinition> maps() {
        return maps;
    }

    public CorpseManager corpses() {
        return corpseManager;
    }
    
    public ArenaManager arena() {
        return arenaManager;
    }

    public void queue(Player player) {
        PlayerProfile profile = profiles.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerProfile(id, player.getName()));
        currentGame.addToQueue(player, profile);
    }

    public void leave(Player player) {
        if (currentGame.hasPlayer(player.getUniqueId())) {
            currentGame.removeFromQueue(player.getUniqueId());
        }
    }

    public void startNow() {
        currentGame.forceStart();
    }

    public MurderMysteryGame game() {
        return currentGame;
    }

    public void shutdown() {
        Bukkit.getOnlinePlayers()
                .forEach(player -> currentGame.removeFromQueue(player.getUniqueId()));
    }

    // ===== Private Helpers =====

    /**
     * Bind a template MapDefinition to the active world.
     * This ensures all spawn locations reference mm_active instead of template worlds.
     */
    private MapDefinition bindToActiveWorld(MapDefinition template, World activeWorld) {
        if (template == null || activeWorld == null) return template;

        Location waiting = template.waitingSpawn();
        Location waitingBound = waiting == null ? null : new Location(
                activeWorld,
                waiting.getX(), waiting.getY(), waiting.getZ(),
                waiting.getYaw(), waiting.getPitch()
        );

        List<Location> boundSpawns = new ArrayList<>();
        for (Location l : template.gameSpawns()) {
            if (l == null) continue;
            boundSpawns.add(new Location(
                    activeWorld,
                    l.getX(), l.getY(), l.getZ(),
                    l.getYaw(), l.getPitch()
            ));
        }

        // IMPORTANT: worldName should be activeWorld.getName(), NOT template world name
        return new MapDefinition(
                template.name(),
                activeWorld.getName(),
                waitingBound,
                boundSpawns,
                null
        );
    }

    private List<MapDefinition> loadMaps(JavaPlugin plugin, MatchPreset defaultPreset) {
        List<MapDefinition> maps = new ArrayList<>();
        ConfigurationSection mapsSection = plugin.getConfig().getConfigurationSection("maps");

        if (mapsSection != null) {
            for (String key : mapsSection.getKeys(false)) {
                ConfigurationSection section = mapsSection.getConfigurationSection(key);
                if (section != null) {
                    maps.add(MapDefinition.fromConfig(key, section));
                }
            }
        }

        // Fallback to default map if no maps configured
        if (maps.isEmpty()) {
            Location defaultLoc = new Location(Bukkit.getWorlds().get(0), 0.5, 70, 0.5);
            maps.add(new MapDefinition(
                    defaultPreset.mapName(),
                    defaultPreset.mapName(),
                    defaultLoc,
                    List.of(defaultLoc),
                    null
            ));
        }

        return maps;
    }

    // ===== Event Listeners =====
    // REMOVED: onJoin/onQuit auto-queueing - now handled by ArenaJoinListener
    // Players are routed through ArenaService first, which handles joinOpen gating
}

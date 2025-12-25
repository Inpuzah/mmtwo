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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
        
        // Look up map from registry
        MapDefinition map = mapRegistry.get(mapName);
        
        if (map == null) {
            plugin.getLogger().warning("[GameManager] ✗ Map not found in registry: " + mapName);
            return;
        }
        
        if (map.world() == null) {
            plugin.getLogger().warning("[GameManager] ✗ World not found for map: " + mapName);
            return;
        }
        
        // Reset game state
        plugin.getLogger().info("[GameManager] Clearing queue and resetting game state");
        currentGame.resetToLobby();
        
        // Set the map
        setNextMap(map);
        
        // Set arena to WAITING state (ready for players)
        arenaManager.setState(ArenaState.WAITING);
        
        plugin.getLogger().info("[GameManager] ✓ Game prepared with map: " + mapName + " - READY FOR PLAYERS");
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        GameState state = currentGame.state();
        if (state == GameState.LOBBY || state == GameState.COUNTDOWN) {
            queue(event.getPlayer());
        } else {
            // Block joins during active gameplay
            event.getPlayer().kickPlayer(ChatColor.RED +
                    "Game already in progress. Please wait for the next round.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer());
    }
}

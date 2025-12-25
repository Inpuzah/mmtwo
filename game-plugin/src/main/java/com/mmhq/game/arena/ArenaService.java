package com.mmhq.game.arena;

import com.mmhq.game.arena.reset.ResetPipeline;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central service for arena management.
 * Handles map preparation, player join logic, and state transitions.
 * This is the "source of truth" for arena state.
 */
public final class ArenaService {
    private final JavaPlugin plugin;
    private final MapRegistry registry;
    private final ResetPipeline resetPipeline;

    private final String activeWorldName;
    private final String lobbyServerName;

    private volatile ArenaState state = ArenaState.IDLE;
    private volatile String currentMapId = null;
    private volatile boolean joinOpen = false;

    private volatile String lastProgressStep = "NONE";
    private volatile int lastProgressPct = 0;
    private volatile String lastError = null;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    public ArenaService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new MapRegistry(plugin);
        this.resetPipeline = new ResetPipeline(plugin);

        this.activeWorldName = plugin.getConfig().getString("arena.activeWorld", "mm_active");
        this.lobbyServerName = plugin.getConfig().getString("arena.lobbyServerName", "lobby");
        
        plugin.getLogger().info("[ArenaService] Initialized - activeWorld=" + activeWorldName + " lobbyServer=" + lobbyServerName);
    }

    // --- Getters ---
    
    public ArenaState state() { return state; }
    public String currentMapId() { return currentMapId; }
    public boolean joinOpen() { return joinOpen; }
    public String lastProgressStep() { return lastProgressStep; }
    public int lastProgressPct() { return lastProgressPct; }
    public String lastError() { return lastError; }
    public String activeWorldName() { return activeWorldName; }
    public MapRegistry registry() { return registry; }

    /**
     * Get a human-readable status string for debugging/messaging.
     */
    public String statusString() {
        return "state=" + state +
                " currentMap=" + (currentMapId == null ? "NONE" : currentMapId) +
                " joinOpen=" + joinOpen +
                " progress=" + lastProgressStep + ":" + lastProgressPct +
                " error=" + (lastError == null ? "NONE" : lastError);
    }

    // --- Control methods ---

    public void setJoinOpen(boolean open) {
        this.joinOpen = open;
        plugin.getLogger().info("[ArenaService] joinOpen set to " + open);
    }

    public void setState(ArenaState newState) {
        this.state = newState;
        plugin.getLogger().info("[ArenaService] State changed to " + newState);
    }

    /**
     * Prepare the arena with a specific map.
     * This triggers a hard reset: unload active world, copy template, reload.
     */
    public void prepare(String mapId) {
        MapDefinition map = registry.get(mapId);
        if (map == null) {
            fail("Unknown map: " + mapId);
            return;
        }
        
        if (!busy.compareAndSet(false, true)) {
            plugin.getLogger().warning("[ArenaService] Prepare ignored; already busy.");
            return;
        }

        joinOpen = false;
        state = ArenaState.PREPARING;
        currentMapId = map.id();
        lastError = null;

        plugin.getLogger().info("[ArenaService] ===== PREPARING MAP: " + map.id() + " (template: " + map.templateWorld() + ") =====");

        // Kick everyone to lobby first (safe even if nobody online)
        kickAllToLobby();

        resetPipeline.hardResetToTemplate(map.templateWorld(), activeWorldName, (step, pct) -> {
            lastProgressStep = step;
            lastProgressPct = pct;
            plugin.getLogger().info("[ArenaService] PREPARE " + map.id() + " progress: " + step + " (" + pct + "%)");
            // TODO: Could send PREPARE_PROGRESS to lobby here
        }).whenComplete((world, err) -> {
            if (err != null) {
                fail("Prepare failed: " + err.getMessage());
                busy.set(false);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    applyWorldRules(world);

                    // Put anyone who joined mid-prepare into waiting spawn
                    LocationUtil.tpAllPlayers(world, map.waitingSpawn(world), GameMode.ADVENTURE);

                    state = ArenaState.WAITING;
                    busy.set(false);

                    plugin.getLogger().info("[ArenaService] ✓ Prepared map " + map.id() + " into " + activeWorldName + " - now in WAITING state");

                } catch (Exception e) {
                    fail("Post-prepare failed: " + e.getMessage());
                    busy.set(false);
                }
            });
        });
    }

    /**
     * Handle a player joining the server.
     * Routes them based on arena state and joinOpen flag.
     */
    public void handlePlayerJoin(Player p) {
        plugin.getLogger().info("[ArenaService] Player joined: " + p.getName() + " | joinOpen=" + joinOpen + " | state=" + state);
        
        if (!joinOpen) {
            // Send them back immediately
            sendToLobby(p);
            p.sendMessage("§cGame server is not open yet.");
            return;
        }

        World w = Bukkit.getWorld(activeWorldName);
        if (w == null || currentMapId == null) {
            sendToLobby(p);
            p.sendMessage("§cArena not ready.");
            return;
        }

        MapDefinition map = registry.get(currentMapId);
        if (map == null) {
            sendToLobby(p);
            p.sendMessage("§cArena map missing.");
            return;
        }

        // Late joiners during active game go to spectator
        if (state == ArenaState.IN_PROGRESS || state == ArenaState.POST_GAME || state == ArenaState.RESETTING) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(map.spectatorSpawn(w));
            p.sendMessage("§eMatch in progress. You are spectating.");
            return;
        }

        // WAITING/COUNTDOWN/PREGAME - send to waiting spawn
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(map.waitingSpawn(w));
        p.sendMessage("§aJoined the match lobby.");
    }

    /**
     * Apply standard game rules to the active world.
     */
    private void applyWorldRules(World w) {
        w.setAutoSave(false);
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(6000);
        w.setGameRuleValue("doDaylightCycle", "false");
        w.setGameRuleValue("doWeatherCycle", "false");
        w.setGameRuleValue("doMobSpawning", "false");
        w.setGameRuleValue("keepInventory", "true");
    }

    /**
     * Kick all online players to the lobby server.
     */
    private void kickAllToLobby() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendToLobby(p);
        }
    }

    /**
     * Send a player to the lobby server via BungeeCord/Velocity.
     */
    private void sendToLobby(Player p) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("Connect");
            out.writeUTF(lobbyServerName);
            p.sendPluginMessage(plugin, "BungeeCord", baos.toByteArray());
            plugin.getLogger().info("[ArenaService] Sending " + p.getName() + " to " + lobbyServerName);
        } catch (Exception e) {
            plugin.getLogger().warning("[ArenaService] Failed to send " + p.getName() + " to lobby: " + e.getMessage());
            // Fallback: try command dispatch (might not work on all setups)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "send " + p.getName() + " " + lobbyServerName);
        }
    }

    /**
     * Set error state with a message.
     */
    private void fail(String msg) {
        lastError = msg;
        state = ArenaState.ERROR;
        plugin.getLogger().severe("[ArenaService] " + msg);
    }
}

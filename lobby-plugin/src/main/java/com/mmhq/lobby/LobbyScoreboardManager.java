package com.mmhq.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * Manages the lobby scoreboard that displays mm1 game status.
 * Polls mm1 periodically and updates all players' scoreboards.
 */
public class LobbyScoreboardManager {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;
    private BukkitTask pollTask;

    // Cached status from mm1
    private String currentState = "UNKNOWN";
    private String currentMap = "---";
    private int playerCount = 0;
    private int maxPlayers = 16;

    public LobbyScoreboardManager(JavaPlugin plugin, GameMessageSender messageSender) {
        this.plugin = plugin;
        this.messageSender = messageSender;
    }

    /**
     * Start polling mm1 for status updates.
     * @param intervalTicks How often to poll (20 ticks = 1 second)
     */
    public void startPolling(long intervalTicks) {
        if (pollTask != null) {
            pollTask.cancel();
        }

        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Only poll if there are players online to send through
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                messageSender.sendStatusQuery();
            }
            // Update scoreboards for all players
            updateAllScoreboards();
        }, 20L, intervalTicks);

        plugin.getLogger().info("[Lobby] Started status polling every " + (intervalTicks / 20) + " seconds");
    }

    /**
     * Stop polling.
     */
    public void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    /**
     * Update the cached status from mm1 response.
     * Expected format: "STATE:mapName:playerCount:maxPlayers"
     * Example: "WAITING:Subway:3:16"
     */
    public void updateStatus(String statusData) {
        plugin.getLogger().info("[Lobby] Received status update: " + statusData);
        
        String[] parts = statusData.split(":");
        if (parts.length >= 1) {
            currentState = parts[0];
        }
        if (parts.length >= 2) {
            currentMap = parts[1].isEmpty() ? "---" : parts[1];
        }
        if (parts.length >= 3) {
            try {
                playerCount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
        }
        if (parts.length >= 4) {
            try {
                maxPlayers = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}
        }

        // Immediately update all scoreboards
        updateAllScoreboards();
    }

    /**
     * Update scoreboards for all online players.
     */
    public void updateAllScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    /**
     * Set up and update the scoreboard for a specific player.
     */
    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();
        
        // Use 2-arg version for 1.8 compatibility; display name set separately
        Objective obj = board.registerNewObjective("mmlobby", "dummy");
        obj.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Murder Mystery");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 10;

        // Header spacer
        setScore(obj, ChatColor.DARK_GRAY + "---------------", score--);

        // Server status
        ChatColor stateColor = getStateColor(currentState);
        setScore(obj, ChatColor.WHITE + "Status: " + stateColor + currentState, score--);

        // Current map
        setScore(obj, ChatColor.WHITE + "Map: " + ChatColor.AQUA + currentMap, score--);

        // Player count
        String playerInfo = playerCount + "/" + maxPlayers;
        ChatColor countColor = playerCount >= maxPlayers ? ChatColor.RED : 
                              playerCount > 0 ? ChatColor.GREEN : ChatColor.GRAY;
        setScore(obj, ChatColor.WHITE + "Players: " + countColor + playerInfo, score--);

        // Spacer
        setScore(obj, " ", score--);

        // Instructions based on state
        if ("WAITING".equals(currentState) || "COUNTDOWN".equals(currentState)) {
            setScore(obj, ChatColor.YELLOW + "» /server mm1 to join!", score--);
        } else if ("IN_PROGRESS".equals(currentState)) {
            setScore(obj, ChatColor.RED + "Game in progress...", score--);
        } else if ("IDLE".equals(currentState)) {
            setScore(obj, ChatColor.GRAY + "No game running", score--);
        } else {
            setScore(obj, ChatColor.GRAY + "Checking status...", score--);
        }

        // Footer spacer
        setScore(obj, ChatColor.DARK_GRAY + "---------------" + ChatColor.RESET, score--);

        // Server name
        setScore(obj, ChatColor.YELLOW + "skyza.app", score--);

        player.setScoreboard(board);
    }

    /**
     * Remove scoreboard from a player.
     */
    public void removeScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void setScore(Objective obj, String text, int score) {
        // Truncate if too long (scoreboard limit)
        if (text.length() > 40) {
            text = text.substring(0, 40);
        }
        Score s = obj.getScore(text);
        s.setScore(score);
    }

    private ChatColor getStateColor(String state) {
        switch (state) {
            case "IDLE": return ChatColor.GRAY;
            case "WAITING": return ChatColor.YELLOW;
            case "COUNTDOWN": return ChatColor.GOLD;
            case "PREGAME": return ChatColor.AQUA;
            case "IN_PROGRESS": return ChatColor.GREEN;
            case "POST_GAME": return ChatColor.LIGHT_PURPLE;
            case "RESET": return ChatColor.RED;
            default: return ChatColor.WHITE;
        }
    }

    // Getters for current status
    public String getCurrentState() { return currentState; }
    public String getCurrentMap() { return currentMap; }
    public int getPlayerCount() { return playerCount; }
    public int getMaxPlayers() { return maxPlayers; }
}

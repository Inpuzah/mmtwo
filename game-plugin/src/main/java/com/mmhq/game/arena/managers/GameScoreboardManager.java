package com.mmhq.game.arena.managers;

import com.mmhq.sharedapi.game.GameState;
import com.mmhq.sharedapi.game.MurderRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GameScoreboardManager {
    private final JavaPlugin plugin;
    private final Map<UUID, MurderRole> playerRoles;
    private final GoldCollectionManager goldManager;
    private final SimpleDateFormat dateFormat;
    private BukkitTask updateTask;
    private final java.util.Set<java.util.UUID> knifeTesters = new java.util.HashSet<>();
    
    private GameState currentState;
    private int innocentsLeft;
    private int timeLeftSeconds;
    private int countdownSeconds;
    private int currentPlayers;
    private int maxPlayers;
    private int minPlayers;
    private boolean detectiveAlive;
    private boolean bowDropped;
    private String currentMapName;
    private String serverId;

    public GameScoreboardManager(JavaPlugin plugin, GoldCollectionManager goldManager, String serverId) {
        this.plugin = plugin;
        this.goldManager = goldManager;
        this.playerRoles = new HashMap<>();
        this.dateFormat = new SimpleDateFormat("MM/dd/yy");
        this.currentState = GameState.LOBBY;
        this.innocentsLeft = 0;
        this.timeLeftSeconds = 0;
        this.currentMapName = "Unknown";
        this.serverId = serverId;
    }

    public void setPlayerRole(UUID playerId, MurderRole role) {
        playerRoles.put(playerId, role);
    }

    public void setState(GameState state) {
        this.currentState = state;
    }

    public void setInnocentsLeft(int count) {
        this.innocentsLeft = count;
    }

    public void setTimeLeft(int seconds) {
        this.timeLeftSeconds = seconds;
    }

    public void setCountdownSeconds(int seconds) {
        this.countdownSeconds = seconds;
    }

    public void setPlayerCounts(int current, int max, int min) {
        this.currentPlayers = current;
        this.maxPlayers = max;
        this.minPlayers = min;
    }

    public void setDetectiveAlive(boolean alive) {
        this.detectiveAlive = alive;
    }

    public void setBowDropped(boolean dropped) {
        this.bowDropped = dropped;
    }

    public void setMapName(String mapName) {
        this.currentMapName = formatMapName(mapName);
    }

    public void enableKnifeTest(java.util.UUID playerId) {
        knifeTesters.add(playerId);
    }

    public void disableKnifeTest(java.util.UUID playerId) {
        knifeTesters.remove(playerId);
    }

    public void startUpdating() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 0L, 20L); // Update every second
    }

    public void stopUpdating() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    public void reset() {
        playerRoles.clear();
        currentState = GameState.LOBBY;
        innocentsLeft = 0;
        timeLeftSeconds = 0;
        countdownSeconds = 0;
    }

    private void updateScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("mm", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatColor.BOLD + "" + ChatColor.YELLOW + "MURDER MYSTERY");

        int score = 15;
        
        // Date and time + Server ID
        String date = dateFormat.format(new Date());
        setScore(objective, ChatColor.GRAY + date + " " + ChatColor.DARK_GRAY + serverId, score--);
        setScore(objective, "", score--); // Blank line

        // Pre-game/Lobby State
        if (currentState == GameState.LOBBY || currentState == GameState.COUNTDOWN) {
            setScore(objective, ChatColor.RED + "Map: " + ChatColor.GREEN + (currentMapName != null && !currentMapName.isEmpty() ? currentMapName : "Loading..."), score--);
            setScore(objective, ChatColor.RED + "Players: " + ChatColor.GREEN + currentPlayers + "/" + maxPlayers, score--);
            setScore(objective, " ", score--); // Blank line
            
            if (currentState == GameState.COUNTDOWN) {
                setScore(objective, ChatColor.YELLOW + "Starting in " + ChatColor.BOLD + countdownSeconds + "s", score--);
            } else {
                setScore(objective, ChatColor.YELLOW + "Waiting for players (" + minPlayers + " needed)", score--);
            }

            // Optional: show knife test status if enabled for this player
            if (knifeTesters.contains(player.getUniqueId())) {
                setScore(objective, ChatColor.AQUA + "Knife Test: " + ChatColor.GREEN + "ON", score--);
            }
        }
        
        // Role
        MurderRole role = playerRoles.get(player.getUniqueId());
        if (role != null && currentState == GameState.IN_GAME) {
            String roleColor = getRoleColor(role);
            String roleName = getRoleName(role);
            setScore(objective, ChatColor.RESET + "Role: " + roleColor + roleName, score--);
        }

        // Innocents Left
        if (currentState == GameState.IN_GAME) {
            setScore(objective, ChatColor.RESET + "Innocents Left: " + ChatColor.GREEN + innocentsLeft, score--);
            
            // Time Left
            int minutes = timeLeftSeconds / 60;
            int seconds = timeLeftSeconds % 60;
            setScore(objective, ChatColor.RESET + "Time Left: " + ChatColor.GREEN + String.format("%d:%02d", minutes, seconds), score--);
            
            setScore(objective, "  ", score--); // Blank line
            
            // Detective status + Bow status
            String det = ChatColor.RESET + "Detective: " + (detectiveAlive ? ChatColor.GREEN + "Alive" : ChatColor.RED + "Down");
            setScore(objective, det, score--);
            String bowStatus = bowDropped ? ChatColor.RED + "Dropped" : ChatColor.GREEN + "Not Dropped";
            setScore(objective, ChatColor.RESET + "Bow: " + bowStatus, score--);
        }

        setScore(objective, "   ", score--); // Blank line
        
        // Map
        setScore(objective, "Map: " + ChatColor.RED + currentMapName, score--);
        
        setScore(objective, "    ", score--); // Blank line
        
        // Footer
        setScore(objective, ChatColor.YELLOW + "Skyza  -  MM2  - Beta", score--);

        player.setScoreboard(scoreboard);
    }

    private void setScore(Objective objective, String text, int score) {
        Team team = objective.getScoreboard().registerNewTeam("line" + score);
        String entry = ChatColor.values()[score % ChatColor.values().length].toString();
        team.addEntry(entry);
        team.setPrefix(text.length() > 40 ? text.substring(0, 40) : text);
        objective.getScore(entry).setScore(score);
    }

    private String formatMapName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String spaced = raw.replace('_', ' ');
        // Insert spaces before capital letters (e.g., AncientTomb -> Ancient Tomb)
        spaced = spaced.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        return spaced.trim();
    }

    private String getRoleColor(MurderRole role) {
        switch (role) {
            case MURDERER:
                return ChatColor.RED.toString();
            case DETECTIVE:
                return ChatColor.BLUE.toString();
            case INNOCENT:
                return ChatColor.GREEN.toString();
            default:
                return ChatColor.GRAY.toString();
        }
    }

    private String getRoleName(MurderRole role) {
        switch (role) {
            case MURDERER:
                return "Murderer";
            case DETECTIVE:
                return "Detective";
            case INNOCENT:
                return "Innocent";
            default:
                return "Spectator";
        }
    }
}

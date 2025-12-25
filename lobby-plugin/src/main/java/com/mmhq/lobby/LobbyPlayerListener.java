package com.mmhq.lobby;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit to manage scoreboards.
 */
public class LobbyPlayerListener implements Listener {

    private final LobbyScoreboardManager scoreboardManager;

    public LobbyPlayerListener(LobbyScoreboardManager scoreboardManager) {
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Give scoreboard to joining player after a short delay (let them fully load)
        event.getPlayer().getServer().getScheduler().runTaskLater(
            event.getPlayer().getServer().getPluginManager().getPlugin("LobbyPlugin"),
            () -> scoreboardManager.updateScoreboard(event.getPlayer()),
            10L // 0.5 second delay
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        scoreboardManager.removeScoreboard(event.getPlayer());
    }
}

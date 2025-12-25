package com.mmhq.game.arena;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events and routes them through ArenaService.
 */
public final class ArenaJoinListener implements Listener {
    private final ArenaService arena;

    public ArenaJoinListener(ArenaService arena) {
        this.arena = arena;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        arena.handlePlayerJoin(e.getPlayer());
    }
}

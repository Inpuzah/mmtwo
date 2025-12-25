package com.mmhq.game.admin;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Allows server operators and admin-permission players to edit map blocks in creative mode.
 * This is useful for quick map fixes without needing to restart.
 */
public final class MapEditorListener implements Listener {
    private final JavaPlugin plugin;

    public MapEditorListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        // Only allow editing in creative mode
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        
        // Check if player is op or has admin permission
        if (!canEditMaps(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to edit maps. (Need op or 'admin' permission group)");
            return;
        }
        
        // Allow the break
        plugin.getLogger().info("[MapEditor] " + player.getName() + " broke block at " + 
                event.getBlock().getLocation().getBlockX() + ", " + 
                event.getBlock().getLocation().getBlockY() + ", " + 
                event.getBlock().getLocation().getBlockZ());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        // Only allow editing in creative mode
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        
        // Check if player is op or has admin permission
        if (!canEditMaps(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to edit maps. (Need op or 'admin' permission group)");
            return;
        }
        
        // Allow the place
        plugin.getLogger().info("[MapEditor] " + player.getName() + " placed " + 
                event.getBlock().getType() + " at " + 
                event.getBlock().getLocation().getBlockX() + ", " + 
                event.getBlock().getLocation().getBlockY() + ", " + 
                event.getBlock().getLocation().getBlockZ());
    }

    /**
     * Check if a player can edit maps.
     * Requirements: op status OR 'admin' permission group.
     */
    private boolean canEditMaps(Player player) {
        if (player.isOp()) {
            return true;
        }
        
        // Check for 'admin' permission group
        return player.hasPermission("group.admin");
    }
}

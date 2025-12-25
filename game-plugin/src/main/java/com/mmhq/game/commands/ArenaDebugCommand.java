package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import com.mmhq.game.arena.ArenaManager;
import com.mmhq.game.arena.ArenaState;
import com.mmhq.sharedapi.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Debug command to check arena and game status.
 * Usage: /mmarena
 */
public final class ArenaDebugCommand implements CommandExecutor {
    private final GameManager gameManager;

    public ArenaDebugCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You need admin permission to use this command.");
            return true;
        }

        ArenaManager arena = gameManager.arena();
        GameState gameState = gameManager.game().state();
        ArenaState arenaState = arena.getState();
        
        // Build detailed status report
        StringBuilder msg = new StringBuilder();
        msg.append("\n" + ChatColor.GOLD + "===== ARENA DEBUG INFO =====\n");
        msg.append(ChatColor.RESET + "GameState: " + ChatColor.YELLOW + gameState + "\n");
        msg.append(ChatColor.RESET + "ArenaState: " + ChatColor.YELLOW + arenaState + "\n");
        msg.append(ChatColor.RESET + "Current Map: " + ChatColor.YELLOW + (arena.getCurrentMap() != null ? arena.getCurrentMap().name() : "NONE") + "\n");
        msg.append(ChatColor.RESET + "Queue Size: " + ChatColor.YELLOW + gameManager.game().getQueueSize() + "\n");
        msg.append(ChatColor.RESET + "\n" + arena.getStatus() + "\n");
        msg.append(ChatColor.GOLD + "=============================\n");
        
        sender.sendMessage(msg.toString());
        return true;
    }
}

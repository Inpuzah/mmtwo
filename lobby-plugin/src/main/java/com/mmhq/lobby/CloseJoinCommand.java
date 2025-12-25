package com.mmhq.lobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command to close joining on mm1.
 * Usage: /closejoin_mm1
 * 
 * Use this to prevent new players from joining mm1.
 */
public class CloseJoinCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;

    public CloseJoinCommand(JavaPlugin plugin, GameMessageSender messageSender) {
        this.plugin = plugin;
        this.messageSender = messageSender;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        messageSender.sendCloseJoin();
        sender.sendMessage("§c[Lobby] Sent CLOSE_JOIN to mm1 - no new players can join");
        plugin.getLogger().info("[Lobby] Admin " + sender.getName() + " closed join on mm1");
        
        return true;
    }
}

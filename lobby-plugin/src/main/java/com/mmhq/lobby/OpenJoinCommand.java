package com.mmhq.lobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command to open joining on mm1.
 * Usage: /openjoin_mm1
 * 
 * After PREPARE completes, use this to allow players to join mm1.
 */
public class OpenJoinCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;

    public OpenJoinCommand(JavaPlugin plugin, GameMessageSender messageSender) {
        this.plugin = plugin;
        this.messageSender = messageSender;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        messageSender.sendOpenJoin();
        sender.sendMessage("§a[Lobby] Sent OPEN_JOIN to mm1 - players can now /server mm1");
        plugin.getLogger().info("[Lobby] Admin " + sender.getName() + " opened join on mm1");
        
        return true;
    }
}

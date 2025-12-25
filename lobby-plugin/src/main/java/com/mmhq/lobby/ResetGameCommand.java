package com.mmhq.lobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ResetGameCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;

    public ResetGameCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageSender = new GameMessageSender(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        // Send reset command to mm1
        messageSender.sendResetArena();
        sender.sendMessage("§a[Lobby] Sent reset command to mm1");
        plugin.getLogger().info("[Lobby] Admin " + sender.getName() + " reset the game arena");
        
        return true;
    }
}

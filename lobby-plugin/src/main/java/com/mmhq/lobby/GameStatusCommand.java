package com.mmhq.lobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class GameStatusCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;

    public GameStatusCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageSender = new GameMessageSender(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        // Send a status query to mm1 server
        messageSender.sendStatusQuery();
        sender.sendMessage("§a[Lobby] Querying game status from mm1...");
        plugin.getLogger().info("[Lobby] Admin " + sender.getName() + " queried game status");
        
        return true;
    }
}

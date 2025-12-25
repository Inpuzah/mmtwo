package com.mmhq.lobby;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command to prepare a game on mm1 with a specific map.
 * Usage: /prepare_mm1 <map>
 * 
 * This sends a PREPARE message to mm1 which triggers:
 * 1. Kick all players to lobby
 * 2. Unload active world
 * 3. Copy template world to active
 * 4. Load active world
 * 5. Set state to WAITING
 */
public class PrepareGameCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GameMessageSender messageSender;
    private final String[] validMaps = {"AncientTomb", "Subway", "HypixelWorld"};

    public PrepareGameCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageSender = new GameMessageSender(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage("§eUsage: /prepare_mm1 <map>");
            sender.sendMessage("§eValid maps: AncientTomb, Subway, HypixelWorld");
            return false;
        }

        String mapName = args[0];
        if (!isValidMap(mapName)) {
            sender.sendMessage("§cUnknown map: " + mapName);
            sender.sendMessage("§eValid maps: AncientTomb, Subway, HypixelWorld");
            return false;
        }

        // Use the new PREPARE message (triggers hard reset)
        messageSender.sendPrepareGame(mapName);
        sender.sendMessage("§a[Lobby] Sent PREPARE for map: " + mapName);
        sender.sendMessage("§7The mm1 server will clone the template world and enter WAITING state.");
        sender.sendMessage("§7Use /openjoin_mm1 to allow players to join when ready.");
        plugin.getLogger().info("[Lobby] Admin " + sender.getName() + " sent PREPARE for map: " + mapName);
        
        return true;
    }

    private boolean isValidMap(String mapName) {
        for (String map : validMaps) {
            if (map.equalsIgnoreCase(mapName)) {
                return true;
            }
        }
        return false;
    }
}

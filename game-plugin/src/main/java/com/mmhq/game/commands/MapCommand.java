package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import com.mmhq.game.arena.MapDefinition;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class MapCommand implements CommandExecutor {
    private final GameManager gameManager;
    private final List<MapDefinition> maps;

    public MapCommand(GameManager gameManager, List<MapDefinition> maps) {
        this.gameManager = gameManager;
        this.maps = maps;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mm.map")) {
            sender.sendMessage("You don't have permission to change maps.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /mmmap <mapname>");
            sender.sendMessage("Available maps: " + mapNames());
            return true;
        }

        String mapName = args[0];
        MapDefinition map = maps.stream()
                .filter(m -> m.name().equalsIgnoreCase(mapName))
                .findFirst()
                .orElse(null);

        if (map == null) {
            sender.sendMessage("Unknown map: " + mapName);
            sender.sendMessage("Available maps: " + mapNames());
            return true;
        }

        gameManager.setNextMap(map);
        sender.sendMessage("Map forced to " + map.name() + " for next round.");
        sender.sendMessage("Map world: " + map.worldName() + ", waiting spawn: " + 
                          (map.waitingSpawn() != null ? 
                           (map.waitingSpawn().getWorld() != null ? map.waitingSpawn().getWorld().getName() : "NULL_WORLD") + 
                           " " + map.waitingSpawn().getBlockX() + "," + map.waitingSpawn().getBlockY() + "," + map.waitingSpawn().getBlockZ()
                           : "NULL"));
        return true;
    }

    private String mapNames() {
        return String.join(", ", maps.stream().map(MapDefinition::name).toArray(String[]::new));
    }
}

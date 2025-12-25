package com.mmhq.proxy.command;

import com.mmhq.proxy.status.MapSelectionRegistry;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Command to prepare a map on mm1 from anywhere in the network.
 * Usage: /preparemm1 <mapName>
 */
public class PrepareMapCommand implements SimpleCommand {

    private static final List<String> VALID_MAPS = Arrays.asList("AncientTomb", "Subway", "HypixelWorld");
    
    private final MapSelectionRegistry mapRegistry;
    private final Logger logger;

    public PrepareMapCommand(MapSelectionRegistry mapRegistry, Logger logger) {
        this.mapRegistry = mapRegistry;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return;
        }

        Player player = (Player) invocation.source();
        
        // Check permission
        if (!player.hasPermission("mm.admin.prepare")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /preparemm1 <mapName>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Valid maps: " + String.join(", ", VALID_MAPS), NamedTextColor.GRAY));
            return;
        }

        String mapName = args[0];
        
        // Validate map name (case-insensitive match)
        String matchedMap = VALID_MAPS.stream()
                .filter(m -> m.equalsIgnoreCase(mapName))
                .findFirst()
                .orElse(null);
        
        if (matchedMap == null) {
            player.sendMessage(Component.text("Unknown map: " + mapName, NamedTextColor.RED));
            player.sendMessage(Component.text("Valid maps: " + String.join(", ", VALID_MAPS), NamedTextColor.GRAY));
            return;
        }

        // Set the map in registry
        mapRegistry.setSelectedMap(matchedMap);
        
        player.sendMessage(Component.text("✓ Map set to: " + matchedMap, NamedTextColor.GREEN));
        player.sendMessage(Component.text("Players joining mm1 will play on " + matchedMap, NamedTextColor.GRAY));
        
        logger.info("[PrepareMap] {} set next map to: {}", player.getUsername(), matchedMap);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            List<String> suggestions = VALID_MAPS.stream()
                    .filter(m -> m.toLowerCase().startsWith(partial))
                    .toList();
            return CompletableFuture.completedFuture(suggestions);
        }
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("mm.admin.prepare");
    }
}

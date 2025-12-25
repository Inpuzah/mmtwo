package com.mmhq.proxy.status;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Listens for players connecting to mm1 and sends the selected map.
 */
public class MapSelectionListener {
    
    private static final MinecraftChannelIdentifier CHANNEL = 
            MinecraftChannelIdentifier.create("mmhq", "mapselect");
    
    private final MapSelectionRegistry mapRegistry;
    private final Logger logger;

    public MapSelectionListener(MapSelectionRegistry mapRegistry, Logger logger) {
        this.mapRegistry = mapRegistry;
        this.logger = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        
        // Only send map selection when joining mm1
        if (!"mm1".equalsIgnoreCase(serverName)) {
            return;
        }

        String selectedMap = mapRegistry.getSelectedMap();
        logger.info("[MapSelection] Player {} joining mm1, sending map selection: {}", 
                player.getUsername(), selectedMap);

        // Send the selected map to mm1 via plugin message
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("SET_MAP");
            out.writeUTF(selectedMap);
            out.flush();

            // Send on the next tick to ensure the player is fully connected
            event.getServer().sendPluginMessage(CHANNEL, baos.toByteArray());
            
            logger.info("[MapSelection] Sent SET_MAP:{} to mm1 for player {}", 
                    selectedMap, player.getUsername());
        } catch (Exception e) {
            logger.error("[MapSelection] Failed to send map selection: {}", e.getMessage());
        }
    }
}

package com.mmhq.game.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.mmhq.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Listens for map selection messages from the Velocity proxy.
 * When a player joins mm1, the proxy sends the selected map on mmhq:mapselect channel.
 */
public final class ProxyMapSelectionListener implements PluginMessageListener {
    
    public static final String CHANNEL = "mmhq:mapselect";
    
    private final JavaPlugin plugin;
    private final GameManager gameManager;

    public ProxyMapSelectionListener(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String command = in.readUTF();
            
            if ("SET_MAP".equals(command)) {
                String mapName = in.readUTF();
                plugin.getLogger().info("[Proxy] Received map selection: " + mapName + " (via player: " + player.getName() + ")");
                
                // Prepare the game with the selected map
                gameManager.prepareGameWithMap(mapName);
                
                plugin.getLogger().info("[Proxy] ✓ Game prepared with map: " + mapName);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Proxy] Error parsing map selection message: " + e.getMessage());
        }
    }
}

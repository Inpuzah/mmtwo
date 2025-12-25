package com.mmhq.lobby;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.mmhq.sharedapi.Constants;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class LobbyPlugin extends JavaPlugin implements PluginMessageListener {

    private GameMessageSender messageSender;
    private LobbyScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        getLogger().info("LobbyPlugin enabled!");
        
        // Initialize message sender (registers BungeeCord channel internally)
        messageSender = new GameMessageSender(this);
        
        // Initialize scoreboard manager
        scoreboardManager = new LobbyScoreboardManager(this, messageSender);
        
        // Register incoming channel for responses from mm1 (via BungeeCord Forward)
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        
        // Register commands
        getCommand("prepare_mm1").setExecutor(new PrepareGameCommand(this));
        getCommand("game_status").setExecutor(new GameStatusCommand(this));
        getCommand("reset_game").setExecutor(new ResetGameCommand(this));
        
        // Register new arena control commands
        if (getCommand("openjoin_mm1") != null) {
            getCommand("openjoin_mm1").setExecutor(new OpenJoinCommand(this, messageSender));
        }
        if (getCommand("closejoin_mm1") != null) {
            getCommand("closejoin_mm1").setExecutor(new CloseJoinCommand(this, messageSender));
        }
        
        // Register player listener for scoreboard management
        getServer().getPluginManager().registerEvents(new LobbyPlayerListener(scoreboardManager), this);
        
        // Start polling mm1 for status (every 3 seconds = 60 ticks)
        scoreboardManager.startPolling(60L);
        
        getLogger().info("Registered commands: /prepare_mm1, /game_status, /reset_game");
        getLogger().info("Scoreboard polling started - status will update every 3 seconds");
    }

    @Override
    public void onDisable() {
        // Stop polling
        if (scoreboardManager != null) {
            scoreboardManager.stopPolling();
        }
        getLogger().info("LobbyPlugin disabled!");
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        // Handle responses from mm1 via BungeeCord
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            
            // Check if this is a forwarded message on our control channel
            if (subchannel.equals(Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL)) {
                short len = in.readShort();
                byte[] payload = new byte[len];
                in.readFully(payload);
                
                // Parse the actual message
                ByteArrayDataInput payloadIn = ByteStreams.newDataInput(payload);
                String command = payloadIn.readUTF();
                
                getLogger().info("[Lobby] Received from mm1: " + command);

                if ("STATUS_RESPONSE".equals(command)) {
                    String statusData = payloadIn.readUTF();
                    getLogger().info("[Lobby] Status data: " + statusData);
                    
                    // Update scoreboard with new status
                    if (scoreboardManager != null) {
                        scoreboardManager.updateStatus(statusData);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("[Lobby] Error parsing message from mm1: " + e.getMessage());
        }
    }

    public GameMessageSender getMessageSender() {
        return messageSender;
    }

    public LobbyScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}

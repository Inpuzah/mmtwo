package com.mmhq.game.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.mmhq.game.arena.ArenaService;
import com.mmhq.sharedapi.Constants;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Listens for plugin messages from the lobby server.
 * Handles PREPARE, OPEN_JOIN, CLOSE_JOIN, STATUS_QUERY, and RESET_ARENA commands.
 */
public final class LobbyMessageListener implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final ArenaService arena;

    public LobbyMessageListener(JavaPlugin plugin, ArenaService arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Handle direct messages on our control channel
        if (Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL.equals(channel)) {
            handleDirectMessage(player, message);
            return;
        }
        
        // Handle BungeeCord forwarded messages
        if ("BungeeCord".equals(channel)) {
            handleBungeeCordMessage(player, message);
        }
    }

    /**
     * Handle messages sent directly to our control channel (from same server or Velocity).
     */
    private void handleDirectMessage(Player player, byte[] message) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String cmd = in.readUTF();
            
            plugin.getLogger().info("[Lobby->mm1] Direct message: " + cmd);
            processCommand(cmd, in, player);
            
        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] Error parsing direct message: " + e.getMessage());
        }
    }

    /**
     * Handle messages forwarded via BungeeCord.
     */
    private void handleBungeeCordMessage(Player player, byte[] message) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            
            // Check if this is a forwarded message on our control channel
            if (!Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL.equals(subchannel)) {
                return;
            }
            
            // Read the forwarded payload
            short len = in.readShort();
            byte[] payload = new byte[len];
            in.readFully(payload);
            
            // Parse the actual command
            ByteArrayDataInput payloadIn = ByteStreams.newDataInput(payload);
            String cmd = payloadIn.readUTF();
            
            plugin.getLogger().info("[Lobby->mm1] BungeeCord forwarded: " + cmd);
            processCommand(cmd, payloadIn, player);
            
        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] Error parsing BungeeCord message: " + e.getMessage());
        }
    }

    /**
     * Process a command from the lobby.
     */
    private void processCommand(String cmd, ByteArrayDataInput in, Player viaPlayer) {
        switch (cmd) {
            case "PREPARE": {
                String mapId = in.readUTF();
                plugin.getLogger().info("[Lobby->mm1] PREPARE map=" + mapId);
                arena.prepare(mapId);
                break;
            }
            case "OPEN_JOIN":
                plugin.getLogger().info("[Lobby->mm1] OPEN_JOIN");
                arena.setJoinOpen(true);
                break;
            case "CLOSE_JOIN":
                plugin.getLogger().info("[Lobby->mm1] CLOSE_JOIN");
                arena.setJoinOpen(false);
                break;
            case "STATUS_QUERY":
                plugin.getLogger().info("[Lobby->mm1] STATUS_QUERY");
                sendStatusResponse(viaPlayer);
                break;
            case "RESET_ARENA":
                plugin.getLogger().info("[Lobby->mm1] RESET_ARENA");
                // Re-prepare current map if set
                if (arena.currentMapId() != null) {
                    arena.prepare(arena.currentMapId());
                }
                break;
            case "START_GAME": {
                // Legacy support: treat START_GAME as PREPARE
                String mapName = in.readUTF();
                plugin.getLogger().info("[Lobby->mm1] START_GAME (legacy) map=" + mapName);
                arena.prepare(mapName);
                break;
            }
            default:
                plugin.getLogger().warning("[Lobby->mm1] Unknown command: " + cmd);
        }
    }

    /**
     * Send status response back to the lobby.
     */
    private void sendStatusResponse(Player viaPlayer) {
        if (viaPlayer == null) {
            plugin.getLogger().warning("[Lobby] Cannot send status response - no player to route through");
            return;
        }
        
        String status = arena.statusString();
        
        try {
            // Build the payload
            ByteArrayOutputStream payloadBaos = new ByteArrayOutputStream();
            DataOutputStream payloadOut = new DataOutputStream(payloadBaos);
            payloadOut.writeUTF("STATUS_RESPONSE");
            payloadOut.writeUTF(status);
            payloadOut.flush();
            byte[] payload = payloadBaos.toByteArray();

            // Build BungeeCord Forward message back to lobby
            ByteArrayOutputStream msgBaos = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBaos);
            msgOut.writeUTF("Forward");
            msgOut.writeUTF("lobby");
            msgOut.writeUTF(Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL);
            msgOut.writeShort(payload.length);
            msgOut.write(payload);
            msgOut.flush();

            viaPlayer.sendPluginMessage(plugin, "BungeeCord", msgBaos.toByteArray());
            plugin.getLogger().info("[Lobby->mm1] Sent STATUS_RESPONSE: " + status);
            
        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] Failed to send status response: " + e.getMessage());
        }
    }
}

package com.mmhq.lobby;

import com.mmhq.sharedapi.Constants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Sends plugin messages to mm1 server via BungeeCord's Forward channel.
 * Messages are forwarded to the "mm1" server using BungeeCord's plugin messaging API.
 */
public class GameMessageSender {

    private final JavaPlugin plugin;
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String TARGET_SERVER = "mm1";

    public GameMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
        // Register BungeeCord channel for forwarding
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
    }

    /**
     * Sends a PREPARE message with the specified map name to the mm1 server.
     * This triggers a hard reset: unload active world, copy template, reload.
     */
    public void sendPrepareGame(String mapName) {
        sendToMm1(out -> {
            out.writeUTF("PREPARE");
            out.writeUTF(mapName);
        });
        plugin.getLogger().info("[Lobby] Sent PREPARE for map: " + mapName + " to " + TARGET_SERVER);
    }

    /**
     * Sends an OPEN_JOIN message to allow players to join mm1.
     */
    public void sendOpenJoin() {
        sendToMm1(out -> out.writeUTF("OPEN_JOIN"));
        plugin.getLogger().info("[Lobby] Sent OPEN_JOIN to " + TARGET_SERVER);
    }

    /**
     * Sends a CLOSE_JOIN message to prevent new players from joining mm1.
     */
    public void sendCloseJoin() {
        sendToMm1(out -> out.writeUTF("CLOSE_JOIN"));
        plugin.getLogger().info("[Lobby] Sent CLOSE_JOIN to " + TARGET_SERVER);
    }

    /**
     * Sends a START_MATCH message to begin the game on mm1.
     */
    public void sendStartMatch() {
        sendToMm1(out -> out.writeUTF("START_MATCH"));
        plugin.getLogger().info("[Lobby] Sent START_MATCH to " + TARGET_SERVER);
    }

    /**
     * Sends a RESET_ARENA message to the mm1 server.
     */
    public void sendResetArena() {
        sendToMm1(out -> out.writeUTF("RESET_ARENA"));
        plugin.getLogger().info("[Lobby] Sent RESET_ARENA to " + TARGET_SERVER);
    }

    /**
     * Sends a STATUS_QUERY message to request game status from mm1.
     */
    public void sendStatusQuery() {
        sendToMm1(out -> out.writeUTF("STATUS_QUERY"));
        plugin.getLogger().info("[Lobby] Sent STATUS_QUERY to " + TARGET_SERVER);
    }

    /**
     * Legacy method - now calls sendPrepareGame instead.
     * @deprecated Use sendPrepareGame instead
     */
    @Deprecated
    public void sendStartGame(String mapName) {
        sendPrepareGame(mapName);
    }

    /**
     * Send a message to mm1 via BungeeCord Forward channel.
     * Format: Forward + targetServer + channelName + messageData
     */
    private void sendToMm1(ThrowingConsumer<DataOutputStream> payloadWriter) {
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length == 0) {
            plugin.getLogger().warning("[Lobby] No online players to send message through!");
            return;
        }

        try {
            // Build the inner payload (our actual message)
            ByteArrayOutputStream payloadBaos = new ByteArrayOutputStream();
            DataOutputStream payloadOut = new DataOutputStream(payloadBaos);
            payloadWriter.accept(payloadOut);
            payloadOut.flush();
            byte[] payload = payloadBaos.toByteArray();

            // Build the BungeeCord Forward message
            ByteArrayOutputStream msgBaos = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBaos);
            
            msgOut.writeUTF("Forward");           // BungeeCord subchannel
            msgOut.writeUTF(TARGET_SERVER);       // Target server name
            msgOut.writeUTF(Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL); // Our channel
            msgOut.writeShort(payload.length);   // Payload length
            msgOut.write(payload);               // Actual payload
            msgOut.flush();

            // Send via BungeeCord channel
            players[0].sendPluginMessage(plugin, BUNGEE_CHANNEL, msgBaos.toByteArray());

        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}

package com.mmhq.game.arena.managers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mmhq.sharedapi.game.GameState;
import com.mmhq.sharedapi.game.ServerStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Periodically publishes server status to the proxy via plugin messaging.
 * Note: plugin messages require an online player as a conduit.
 */
public final class HeartbeatPublisher {
    private static final String CHANNEL = "mmhq:status";

    private final JavaPlugin plugin;
    private final Supplier<ServerStatus> statusSupplier;
    private BukkitTask task;

    public HeartbeatPublisher(JavaPlugin plugin, Supplier<ServerStatus> statusSupplier) {
        this.plugin = plugin;
        this.statusSupplier = statusSupplier;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sendHeartbeat, 20L, 100L); // 1s delay, then every 5s
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void sendHeartbeat() {
        // Don't send heartbeats during shutdown
        if (!plugin.isEnabled()) {
            return;
        }
        
        ServerStatus status = statusSupplier.get();
        Optional<Player> conduit = pickAnyOnline();
        if (conduit.isEmpty()) {
            return; // No player to carry the message right now
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(status.toPayload());
        conduit.get().sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    private Optional<Player> pickAnyOnline() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return Optional.empty();
        return players.stream().map(p -> (Player) p).findFirst();
    }
}

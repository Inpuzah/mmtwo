package com.mmhq.proxy.queue;

import com.mmhq.proxy.status.ServerStatusRegistry;
import com.mmhq.sharedapi.queue.QueueTicket;
import com.mmhq.sharedapi.game.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Optional;

public final class QueueDispatcher {
    private final ProxyServer proxy;
    private final QueueService queueService;
    private final ServerStatusRegistry registry;
    private final Logger logger;

    public QueueDispatcher(ProxyServer proxy, QueueService queueService, ServerStatusRegistry registry, Logger logger) {
        this.proxy = proxy;
        this.queueService = queueService;
        this.registry = registry;
        this.logger = logger;
    }

    public void tryDispatch() {
        while (true) {
            Optional<QueueTicket> opt = queueService.peek();
            if (opt.isEmpty()) return;
            QueueTicket ticket = opt.get();

            Optional<ServerStatus> joinable = registry.findJoinable(ticket.presetId());
            if (joinable.isEmpty()) return; // No joinable server for this preset yet

            Optional<Player> player = proxy.getPlayer(ticket.playerId());
            if (player.isEmpty()) {
                queueService.remove(ticket);
                continue;
            }

            Optional<RegisteredServer> target = proxy.getServer(joinable.get().serverId());
            if (target.isEmpty()) {
                logger.warn("No registered server named {} to send player {}", joinable.get().serverId(), ticket.playerId());
                queueService.remove(ticket);
                continue;
            }

            player.get().createConnectionRequest(target.get()).connect();
            queueService.remove(ticket);
            logger.info("Sent player {} to server {} (preset {})", player.get().getUsername(), joinable.get().serverId(), ticket.presetId());
        }
    }
}

package com.mmhq.proxy.status;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.mmhq.proxy.queue.QueueDispatcher;
import com.mmhq.sharedapi.game.ServerStatus;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * Receives heartbeats from game servers over plugin messaging.
 */
public final class HeartbeatListener {
    private static final String CHANNEL = "mmhq:status";

    private final ServerStatusRegistry registry;
    private final QueueDispatcher dispatcher;
    private final Logger logger;

    public HeartbeatListener(ServerStatusRegistry registry, QueueDispatcher dispatcher, Logger logger) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equalsIgnoreCase(CHANNEL)) {
            return;
        }
        byte[] data = event.getData();
        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String payload = in.readUTF();
        try {
            ServerStatus status = ServerStatus.fromPayload(payload);
            registry.update(status);
            dispatcher.tryDispatch();
        } catch (Exception ex) {
            logger.warn("Failed to parse heartbeat payload: {}", payload, ex);
        }
    }
}

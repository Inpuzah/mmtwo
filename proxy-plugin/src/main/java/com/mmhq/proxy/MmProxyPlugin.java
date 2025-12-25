package com.mmhq.proxy;

import com.google.inject.Inject;
import com.mmhq.proxy.command.AnnounceCommand;
import com.mmhq.proxy.command.PartyCommand;
import com.mmhq.proxy.command.PrepareMapCommand;
import com.mmhq.proxy.command.QueueCommand;
import com.mmhq.proxy.queue.QueueService;
import com.mmhq.proxy.queue.QueueDispatcher;
import com.mmhq.proxy.status.MapSelectionListener;
import com.mmhq.proxy.status.MapSelectionRegistry;
import com.mmhq.proxy.status.ServerStatusRegistry;
import com.mmhq.proxy.status.HeartbeatListener;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "mmhq-murder-proxy", name = "MMHQ Murder Proxy", version = "0.1.0-SNAPSHOT", authors = {"mmhq"})
public final class MmProxyPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final QueueService queueService;
    private final QueueDispatcher queueDispatcher;
    private final ServerStatusRegistry statusRegistry;
    private final MapSelectionRegistry mapSelectionRegistry;

    @Inject
    public MmProxyPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.queueService = new QueueService();
        this.statusRegistry = new ServerStatusRegistry();
        this.mapSelectionRegistry = new MapSelectionRegistry();
        this.queueDispatcher = new QueueDispatcher(server, queueService, statusRegistry, logger);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("MMHQ Murder Proxy initializing in {}", dataDirectory);
        
        // Register channels
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("mmhq", "status"));
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("mmhq", "mapselect"));
        
        // Register commands
        registerCommands(server.getCommandManager());
        
        // Register event listeners
        server.getEventManager().register(this, new HeartbeatListener(statusRegistry, queueDispatcher, logger));
        server.getEventManager().register(this, new MapSelectionListener(mapSelectionRegistry, logger));
        
        logger.info("MMHQ Murder Proxy ready! Use /preparemm1 <map> to set the game map.");
    }

    private void registerCommands(CommandManager commandManager) {
        commandManager.register(
                commandManager.metaBuilder("mmqueue").plugin(this).build(),
            new QueueCommand(queueService, queueDispatcher, logger)
        );

        commandManager.register(
                commandManager.metaBuilder("mmparty").plugin(this).build(),
                new PartyCommand(logger)
        );

        commandManager.register(
                commandManager.metaBuilder("mmannounce").plugin(this).build(),
                new AnnounceCommand(logger)
        );
        
        // Map preparation command - works from any server
        commandManager.register(
                commandManager.metaBuilder("preparemm1")
                        .aliases("pm1", "setmap")
                        .plugin(this)
                        .build(),
                new PrepareMapCommand(mapSelectionRegistry, logger)
        );
    }
}

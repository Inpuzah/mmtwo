package com.mmhq.game;

import com.mmhq.game.admin.MapEditorListener;
import com.mmhq.game.arena.ArenaJoinListener;
import com.mmhq.game.arena.ArenaManager;
import com.mmhq.game.arena.ArenaService;
import com.mmhq.game.commands.JoinCommand;
import com.mmhq.game.commands.KnifeTestCommand;
import com.mmhq.game.commands.CorpseTestCommand;
import com.mmhq.game.commands.LeaveCommand;
import com.mmhq.game.commands.MapCommand;
import com.mmhq.game.commands.StartCommand;
import com.mmhq.game.messaging.LobbyMessageListener;
import com.mmhq.game.messaging.ProxyMapSelectionListener;
import com.mmhq.game.utils.VersionCompat;
import com.mmhq.sharedapi.Constants;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmGamePlugin extends JavaPlugin {
    private GameManager gameManager;
    private ArenaManager arenaManager;
    private ArenaService arenaService;

    @Override
    public void onEnable() {
        // Load and save default configuration
        saveDefaultConfig();

        // Log version compatibility info
        VersionCompat.logVersionInfo(this);

        // Initialize arena service (new hard-reset system)
        this.arenaService = new ArenaService(this);

        // Initialize legacy arena manager (for existing game logic)
        this.arenaManager = new ArenaManager(this);

        // Initialize game manager (before registering listener!)
        this.gameManager = new GameManager(this, arenaManager);

        // Register plugin messaging channels
        // BungeeCord channel for cross-server communication
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", 
                new LobbyMessageListener(this, arenaService));
        
        // Control channel for direct messages (Velocity)
        getServer().getMessenger().registerIncomingPluginChannel(this, Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL,
                new LobbyMessageListener(this, arenaService));
        getServer().getMessenger().registerOutgoingPluginChannel(this, Constants.PLUGIN_MESSAGE_CHANNEL_CONTROL);
        
        // Status channel for heartbeat publisher
        getServer().getMessenger().registerOutgoingPluginChannel(this, Constants.PLUGIN_MESSAGE_CHANNEL_STATUS);
        
        // Velocity proxy map selection channel
        getServer().getMessenger().registerIncomingPluginChannel(this, 
                ProxyMapSelectionListener.CHANNEL, 
                new ProxyMapSelectionListener(this, gameManager));

        // Initialize corpse manager (after dependencies are loaded)
        gameManager.corpses().init();

        // Register arena join listener (handles routing via ArenaService)
        getServer().getPluginManager().registerEvents(new ArenaJoinListener(arenaService), this);

        // Register commands and event listeners
        registerCommands();
        getServer().getPluginManager().registerEvents(gameManager, this);
        
        // Register map editor listener for ops/admins in creative mode
        getServer().getPluginManager().registerEvents(new MapEditorListener(this), this);

        getLogger().info("MMHQ Murder Mystery Plugin enabled on " + VersionCompat.getVersion());
        getLogger().info("ArenaService ready - active world: " + arenaService.activeWorldName());
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.setState(com.mmhq.game.arena.ArenaState.RESETTING);
        }
    }

    /**
     * Get the ArenaService instance.
     */
    public ArenaService arenaService() {
        return arenaService;
    }

    /**
     * Register all plugin commands.
     */
    private void registerCommands() {
        getCommand("mmjoin").setExecutor(new JoinCommand(gameManager));
        getCommand("mmleave").setExecutor(new LeaveCommand(gameManager));
        getCommand("mmstart").setExecutor(new StartCommand(gameManager));
        getCommand("mmmap").setExecutor(new MapCommand(gameManager, gameManager.maps()));

        // Optional knife test command for development
        if (getCommand("mmknifetest") != null) {
            getCommand("mmknifetest").setExecutor(new KnifeTestCommand(gameManager));
        }

        // Corpse test command for development
        if (getCommand("mmcorpse") != null) {
            getCommand("mmcorpse").setExecutor(new CorpseTestCommand(gameManager.corpses()));
        }
        
        // Arena debug command
        if (getCommand("mmarena") != null) {
            getCommand("mmarena").setExecutor(new com.mmhq.game.commands.ArenaDebugCommand(gameManager));
        }
    }
}

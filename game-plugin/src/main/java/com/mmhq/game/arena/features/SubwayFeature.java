package com.mmhq.game.arena.features;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Subway map feature: Moving train and contact traps.
 * - Train moves on a path and kills players on contact
 * - Traps trigger and kill players if they stay in trap zones
 */
public final class SubwayFeature implements MapFeature, Listener {
    private final JavaPlugin plugin;
    private final World world;
    private BukkitTask trainTask;
    private BukkitTask trapTask;
    private static final boolean DEBUG = true;

    // Train path (TODO: load from config)
    private double trainX = -80.0;
    private final double trainY = 86.0;
    private final double trainZ = 0.0;
    private final double trainSpeed = 0.5;
    private final double trainMaxX = 80.0;
    private int trainTickCount = 0;
    private int trainKillCount = 0;

    public SubwayFeature(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public void init() {
        if (world == null) {
            plugin.getLogger().warning("[Subway] World not found");
            debugLog("init() failed: world is null");
            return;
        }
        
        debugLog("init() starting - world: " + world.getName() + ", train path: X[-80, 80] Y86 Z0");
        trainTickCount = 0;
        trainKillCount = 0;
        plugin.getLogger().info("[Subway] Feature initialized - train route prepared, traps armed");
        debugLog("init() complete");
    }

    @Override
    public void start() {
        debugLog("start() called - spawning train loop");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Start train movement loop
        trainTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                updateTrain();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Periodic trap check
        trapTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                checkTraps();
            }
        }.runTaskTimer(plugin, 0L, 5L);
        
        plugin.getLogger().info("[Subway] Feature STARTED - train moving, traps active");
        debugLog("start() complete - train task: " + trainTask.getTaskId() + ", trap task: " + trapTask.getTaskId());
    }

    @Override
    public void stop() {
        debugLog("stop() called");
        if (trainTask != null) {
            trainTask.cancel();
            trainTask = null;
        }
        if (trapTask != null) {
            trapTask.cancel();
            trapTask = null;
        }
        plugin.getLogger().info("[Subway] Feature STOPPED - train halted (" + trainKillCount + " kills total)");
        debugLog("stop() complete");
    }

    @Override
    public void cleanup() {
        stop();
        debugLog("cleanup() complete");
        plugin.getLogger().info("[Subway] Feature CLEANUP - cleared " + trainKillCount + " train kills");
    }

    @Override
    public String getName() {
        return "Subway";
    }

    private void updateTrain() {
        trainTickCount++;
        
        // Move train along X axis
        trainX += trainSpeed;
        
        // Bounce train at boundaries
        if (trainX > trainMaxX) {
            debugLog("updateTrain() tick " + trainTickCount + " - train bouncing back, X reset from " + trainX);
            trainX = -80.0;
        }
        
        // Check for player contact with train
        Location trainLoc = new Location(world, trainX, trainY, trainZ);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(trainLoc) < 4.0) {
                plugin.getLogger().info("[Subway] TRAIN KILL - " + player.getName() + " hit at X" + String.format("%.1f", trainX));
                debugLog("updateTrain() - player " + player.getName() + " KILLED by train at X" + String.format("%.1f", trainX));
                trainKillCount++;
                player.setHealth(0);
            }
        }
    }

    private void checkTraps() {
        // TODO: Define trap zones in config
        // For now, stub implementation
        
        // Example: trap zone at (0, 86, -50) with radius 5
        Location trapZone = new Location(world, 0, 86, -50);
        double trapRadius = 5.0;
        
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(trapZone) < trapRadius * trapRadius) {
                // Player in trap - could apply poison, damage, or instant kill
                // For now, stub
                debugLog("checkTraps() - player " + player.getName() + " in trap zone");
                // player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*5, 2, true));
            }
        }
    }
    
    private void debugLog(String msg) {
        if (DEBUG) plugin.getLogger().info("[DEBUG-Subway] " + msg);
    }
}

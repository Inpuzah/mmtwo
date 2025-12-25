package com.mmhq.game.arena.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;

public final class GoldSpawnManager {
    private final JavaPlugin plugin;
    private final List<Location> spawnLocations;
    private final List<Item> activeGold;
    private final Random random;
    private final Map<UUID, ?> queuedPlayers; // Reference to players in game
    private BukkitTask spawnTask;

    public GoldSpawnManager(JavaPlugin plugin, List<Location> spawnLocations, Map<UUID, ?> queuedPlayers) {
        this.plugin = plugin;
        this.spawnLocations = new ArrayList<>(spawnLocations);
        this.activeGold = new ArrayList<>();
        this.random = new Random();
        this.queuedPlayers = queuedPlayers;
    }

    public void startSpawning() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        
        // Spawn gold every ~7 seconds in a circle around a random player
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!queuedPlayers.isEmpty()) {
                // Get a random player
                List<UUID> playerIds = new ArrayList<>(queuedPlayers.keySet());
                UUID randomPlayerId = playerIds.get(random.nextInt(playerIds.size()));
                Player player = Bukkit.getPlayer(randomPlayerId);
                
                if (player != null && player.isOnline()) {
                    // Spawn in a 8-block radius circle around player
                    Location playerLoc = player.getLocation();
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = 4.0 + random.nextDouble() * 4.0; // 4-8 blocks radius
                    double x = playerLoc.getX() + Math.cos(angle) * radius;
                    double z = playerLoc.getZ() + Math.sin(angle) * radius;
                    Location spawnLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY() + 0.5, z);
                    spawnGold(spawnLoc);
                }
            }
        }, 0L, 140L); // ~7 seconds in ticks
    }
    
    private Location findNearestSpawn(Location playerLocation) {
        Location nearest = spawnLocations.get(0);
        double minDistance = Double.MAX_VALUE;
        
        for (Location spawn : spawnLocations) {
            double distance = spawn.distanceSquared(playerLocation);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = spawn;
            }
        }
        
        return nearest;
    }

    public void stopSpawning() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        clearAllGold();
    }

    private void spawnGold(Location location) {
        ItemStack gold = new ItemStack(Material.GOLD_INGOT, 1);
        Item item = location.getWorld().dropItem(location.clone().add(0.5, 0.5, 0.5), gold);
        item.setPickupDelay(0);
        activeGold.add(item);
    }

    public void clearAllGold() {
        for (Item item : activeGold) {
            if (item != null && item.isValid()) {
                item.remove();
            }
        }
        activeGold.clear();
    }

    public void removeGold(Item item) {
        activeGold.remove(item);
    }
}

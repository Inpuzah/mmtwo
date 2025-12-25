package com.mmhq.game.arena.managers;

import com.mmhq.sharedapi.game.MurderRole;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GoldCollectionManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> goldCounts;
    private final Map<UUID, MurderRole> playerRoles;
    private boolean bowDropped;
    private DetectiveBowDropManager bowDropManager;

    public GoldCollectionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.goldCounts = new HashMap<>();
        this.playerRoles = new HashMap<>();
        this.bowDropped = false;
    }

    public void setBowDropManager(DetectiveBowDropManager mgr) {
        this.bowDropManager = mgr;
    }

    public void setPlayerRole(UUID playerId, MurderRole role) {
        playerRoles.put(playerId, role);
        goldCounts.put(playerId, 0);
    }

    public int getGoldCount(UUID playerId) {
        return goldCounts.getOrDefault(playerId, 0);
    }

    public boolean isBowDropped() {
        return bowDropped;
    }

    public void setBowDropped(boolean dropped) {
        this.bowDropped = dropped;
    }

    public void reset() {
        goldCounts.clear();
        playerRoles.clear();
        bowDropped = false;
        if (bowDropManager != null) bowDropManager.clear();
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem().getItemStack();

        if (item.getType() != Material.GOLD_INGOT) {
            return;
        }

        UUID playerId = player.getUniqueId();
        MurderRole role = playerRoles.get(playerId);

        if (role == null) {
            return; // Not in game
        }

        // Prevent default pickup behavior
        event.setCancelled(true);

        int currentGold = goldCounts.getOrDefault(playerId, 0);
        currentGold += item.getAmount();
        goldCounts.put(playerId, currentGold);
        
        // Always update slot 9 with current gold count (max 64 in stack)
        ItemStack goldStack = new ItemStack(Material.GOLD_INGOT, Math.min(currentGold, 64));
        player.getInventory().setItem(8, goldStack);
        
        plugin.getLogger().info("[MM] DEBUG: Gold pickup - Player: " + player.getName() + ", Amount: " + item.getAmount() + ", Total: " + currentGold + ", Slot: 8 (displayed as 9)");
        
        player.sendMessage(ChatColor.GOLD + "Gold: " + currentGold + "/10");
        
        // Remove the dropped item
        event.getItem().remove();
        
        // Give bow at 10 gold (innocents only)
        if (currentGold >= 10 && role == MurderRole.INNOCENT) {
            goldCounts.put(playerId, currentGold - 10); // Deduct 10 gold
            
            // Update gold display after deduction
            int remainingGold = currentGold - 10;
            if (remainingGold > 0) {
                player.getInventory().setItem(8, new ItemStack(Material.GOLD_INGOT, Math.min(remainingGold, 64)));
            } else {
                player.getInventory().setItem(8, new ItemStack(Material.GOLD_INGOT, 1));
            }
            
            giveBow(player);
            player.sendMessage(ChatColor.GREEN + "You received a bow for collecting 10 gold!");
        }
    }

    // Modern handler removed for compatibility; relying on PlayerPickupItemEvent which is still fired

    private void giveBow(Player player) {
        ItemStack bow = new ItemStack(Material.BOW, 1);
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        // Place bow in hotbar slot 2 and arrow off-hotbar in slot 11 (index 10)
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(10, arrow);
    }
}

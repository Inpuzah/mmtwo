package com.mmhq.game.arena.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import com.mmhq.sharedapi.game.MurderRole;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Manages the detective bow drop: an invisible rotating armor stand holding a bow.
 * Auto pickup by innocents removes the stand and grants the bow.
 */
public final class DetectiveBowDropManager {
    private final JavaPlugin plugin;
    private ArmorStand stand;
    private BukkitTask spinTask;
    private boolean dropped;
    private Location dropLocation;

    public DetectiveBowDropManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stand = null;
        this.spinTask = null;
        this.dropped = false;
        this.dropLocation = null;
    }

    public boolean isDropped() {
        return dropped;
    }

    public Location getDropLocation() {
        return dropLocation;
    }

    public void dropBow(Location loc) {
        clear();
        World world = loc.getWorld();
        if (world == null) return;
        dropLocation = loc.clone().add(0, 0.5, 0);
        stand = world.spawn(dropLocation, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(false);
        stand.setBasePlate(false);
        stand.setCanPickupItems(false);
        stand.setArms(false);
        stand.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.PI / 2, 0, Math.PI / 2));
        stand.setRightArmPose(new org.bukkit.util.EulerAngle(Math.PI / 2, 0, Math.PI / 2));
        stand.setHelmet(new ItemStack(Material.BOW));
        dropped = true;

        // Continuous 360 degree rotation via body pose
        spinTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (stand != null && stand.isValid()) {
                Location standLoc = stand.getLocation();
                standLoc.setYaw(standLoc.getYaw() + 2);
                stand.teleport(standLoc);
            }
        }, 0L, 2L); // Rotate every 2 ticks for smooth 360 rotation
    }

    public boolean tryPickup(Player player, MurderRole role) {
        if (!dropped || stand == null || !stand.isValid()) return false;
        if (role != MurderRole.INNOCENT) return false; // Only innocents can pick up
        if (!player.getWorld().equals(stand.getWorld())) return false;
        if (player.getLocation().distance(stand.getLocation()) > 1.5) return false;

        // Grant tagged detective bow
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Detective Bow");
            bow.setItemMeta(meta);
        }
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(10, new ItemStack(Material.ARROW)); // Off-hotbar inventory slot
        clear();
        return true;
    }

    public void clear() {
        dropped = false;
        dropLocation = null;
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
        if (stand != null) {
            if (stand.isValid()) stand.remove();
            stand = null;
        }
    }
}

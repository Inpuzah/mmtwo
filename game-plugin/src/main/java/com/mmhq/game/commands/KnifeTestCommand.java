package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KnifeTestCommand implements CommandExecutor {
    private final GameManager manager;

    public KnifeTestCommand(GameManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        if (!sender.hasPermission("mm.knifetest")) {
            sender.sendMessage(ChatColor.RED + "You lack permission: mm.knifetest");
            return true;
        }

        boolean enable = true;
        if (args.length > 0) {
            String a = args[0].toLowerCase();
            enable = a.equals("on") || a.equals("enable") || a.equals("true");
            if (a.equals("off") || a.equals("disable") || a.equals("false")) {
                enable = false;
            }
        }

        if (enable) {
            manager.game().enableKnifeTest(player.getUniqueId());
            // Give an iron sword in slot 1 so right-click works
            try {
                player.getInventory().setItem(1, new ItemStack(Material.IRON_SWORD));
                player.getInventory().setHeldItemSlot(1);
            } catch (Throwable ignored) {}
            player.sendMessage(ChatColor.GREEN + "Knife test enabled. Right-click the sword to throw (lobby allowed).");
        } else {
            manager.game().disableKnifeTest(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Knife test disabled.");
        }
        return true;
    }
}
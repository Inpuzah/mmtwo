package com.mmhq.game.commands;

import com.mmhq.game.arena.special.CorpseManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Development command to test corpse spawning.
 * /mmcorpse or /mmcorpse <playerName>
 */
public class CorpseTestCommand implements CommandExecutor {
    private final CorpseManager corpseManager;

    public CorpseTestCommand(CorpseManager corpseManager) {
        this.corpseManager = corpseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // /mmcorpse - spawn corpse of self
            corpseManager.spawnCorpse(player, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Spawned your corpse at " + formatLoc(player.getLocation()) + " (TTL 30s)");
            return true;
        } else if (args.length == 1) {
            // /mmcorpse <playerName>
            Player target = player.getServer().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }

            corpseManager.spawnCorpse(target, target.getLocation());
            player.sendMessage(ChatColor.GREEN + "Spawned corpse of " + target.getName() + " at " + formatLoc(target.getLocation()) + " (TTL 30s)");
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /mmcorpse [playerName]");
            return true;
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}

package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LeaveCommand implements CommandExecutor {
    private final GameManager gameManager;

    public LeaveCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can leave the queue.");
            return true;
        }
        gameManager.leave((Player) sender);
        sender.sendMessage("You left the queue.");
        return true;
    }
}

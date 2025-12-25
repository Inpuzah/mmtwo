package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JoinCommand implements CommandExecutor {
    private final GameManager gameManager;

    public JoinCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join the queue.");
            return true;
        }
        gameManager.queue((Player) sender);
        return true;
    }
}

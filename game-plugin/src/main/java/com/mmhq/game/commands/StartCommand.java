package com.mmhq.game.commands;

import com.mmhq.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StartCommand implements CommandExecutor {
    private final GameManager gameManager;

    public StartCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("mm.start")) {
            sender.sendMessage("You need mm.start to force start.");
            return true;
        }
        gameManager.startNow();
        sender.sendMessage("Forcing game start.");
        return true;
    }
}

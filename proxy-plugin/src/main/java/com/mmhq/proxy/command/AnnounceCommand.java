package com.mmhq.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public final class AnnounceCommand implements SimpleCommand {
    private final Logger logger;

    public AnnounceCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        invocation.source().sendMessage(Component.text("Announcement broadcast stub."));
        logger.info("Announcement requested by {}", invocation.source().getClass().getSimpleName());
    }
}

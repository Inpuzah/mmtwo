package com.mmhq.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public final class PartyCommand implements SimpleCommand {
    private final Logger logger;

    public PartyCommand(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        invocation.source().sendMessage(Component.text("Party command stub: invite/join/leave not yet implemented."));
        logger.debug("Party command invoked by {}", invocation.source().getClass().getSimpleName());
    }
}

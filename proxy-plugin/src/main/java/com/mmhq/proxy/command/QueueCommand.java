package com.mmhq.proxy.command;

import com.mmhq.proxy.queue.QueueService;
import com.mmhq.proxy.queue.QueueDispatcher;
import com.mmhq.sharedapi.queue.QueueTicket;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;


public final class QueueCommand implements SimpleCommand {
    private final QueueService queueService;
    private final QueueDispatcher dispatcher;
    private final Logger logger;

    public QueueCommand(QueueService queueService, QueueDispatcher dispatcher, Logger logger) {
        this.queueService = queueService;
        this.dispatcher = dispatcher;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(queueService.text("Only players can queue."));
            return;
        }

        String preset = invocation.arguments().length > 0 ? invocation.arguments()[0] : "default";
        QueueTicket ticket = queueService.enqueue(player.getUniqueId(), preset);
        invocation.source().sendMessage(queueService.text("Joined queue for preset " + preset + ". Position: " + queueService.position(ticket)));
        logger.info("Player {} joined queue for {}", player.getUniqueId(), preset);
        dispatcher.tryDispatch();
    }
}

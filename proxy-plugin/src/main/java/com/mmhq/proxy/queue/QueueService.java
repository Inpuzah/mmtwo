package com.mmhq.proxy.queue;

import com.mmhq.sharedapi.queue.QueueTicket;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

public final class QueueService {
    private final Deque<QueueTicket> queue = new ArrayDeque<>();

    public QueueTicket enqueue(UUID playerId, String presetId) {
        QueueTicket ticket = new QueueTicket(playerId, presetId);
        queue.addLast(ticket);
        return ticket;
    }

    public Optional<QueueTicket> peek() {
        return Optional.ofNullable(queue.peekFirst());
    }

    public void remove(QueueTicket ticket) {
        queue.remove(ticket);
    }

    public Optional<QueueTicket> nextTicket() {
        return Optional.ofNullable(queue.pollFirst());
    }

    public int position(QueueTicket ticket) {
        int index = 1;
        for (QueueTicket current : queue) {
            if (current.equals(ticket)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public Component text(String message) {
        return Component.text(message);
    }

    public Duration averageWait() {
        if (queue.isEmpty()) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(queue.size() * 10L);
    }
}

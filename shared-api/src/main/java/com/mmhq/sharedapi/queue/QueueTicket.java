package com.mmhq.sharedapi.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class QueueTicket {
    private final UUID playerId;
    private final String presetId;
    private final Instant createdAt;

    public QueueTicket(UUID playerId, String presetId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.presetId = Objects.requireNonNull(presetId, "presetId");
        this.createdAt = Instant.now();
    }

    public UUID playerId() {
        return playerId;
    }

    public String presetId() {
        return presetId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

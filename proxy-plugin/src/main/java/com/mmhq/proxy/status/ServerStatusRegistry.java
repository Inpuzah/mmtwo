package com.mmhq.proxy.status;

import com.mmhq.sharedapi.game.GameState;
import com.mmhq.sharedapi.game.ServerStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerStatusRegistry {
    private final Map<String, TimedStatus> statuses = new ConcurrentHashMap<>();

    public void update(ServerStatus status) {
        statuses.put(status.serverId(), new TimedStatus(status, Instant.now()));
    }

    public Optional<ServerStatus> findJoinable(String presetId) {
        return statuses.values().stream()
                .map(TimedStatus::status)
                .filter(s -> s.presetId().equalsIgnoreCase(presetId))
                .filter(s -> s.joinable() && s.currentPlayers() < s.maxPlayers())
                .findFirst();
    }

    public Collection<ServerStatus> all() {
        return statuses.values().stream().map(TimedStatus::status).toList();
    }

    private record TimedStatus(ServerStatus status, Instant seenAt) { }
}

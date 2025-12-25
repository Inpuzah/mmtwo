package com.mmhq.sharedapi.game;

import java.time.Duration;
import java.util.Objects;

public final class MatchPreset {
    private final String id;
    private final int minPlayers;
    private final int maxPlayers;
    private final Duration countdownLength;
    private final Duration roundLength;
    private final String mapName;

    public MatchPreset(String id, int minPlayers, int maxPlayers, Duration countdownLength, Duration roundLength, String mapName) {
        this.id = Objects.requireNonNull(id, "id");
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.countdownLength = Objects.requireNonNull(countdownLength, "countdownLength");
        this.roundLength = Objects.requireNonNull(roundLength, "roundLength");
        this.mapName = Objects.requireNonNull(mapName, "mapName");
    }

    public String id() {
        return id;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public Duration countdownLength() {
        return countdownLength;
    }

    public Duration roundLength() {
        return roundLength;
    }

    public String mapName() {
        return mapName;
    }
}

package com.mmhq.sharedapi.event;

import com.mmhq.sharedapi.game.GameState;

import java.time.Instant;
import java.util.Objects;

public final class PhaseChangeEvent implements GameEvent {
    private final GameState previous;
    private final GameState current;
    private final Instant occurredAt = Instant.now();

    public PhaseChangeEvent(GameState previous, GameState current) {
        this.previous = Objects.requireNonNull(previous, "previous");
        this.current = Objects.requireNonNull(current, "current");
    }

    public GameState previous() {
        return previous;
    }

    public GameState current() {
        return current;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String description() {
        return "Phase changed from " + previous + " to " + current;
    }
}

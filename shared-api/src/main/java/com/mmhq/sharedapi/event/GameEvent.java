package com.mmhq.sharedapi.event;

import java.time.Instant;

public interface GameEvent {
    Instant occurredAt();
    String description();
}

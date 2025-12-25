package com.mmhq.sharedapi.player;

import com.mmhq.sharedapi.game.MurderRole;

import java.util.Objects;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID uniqueId;
    private final String name;
    private MurderRole lastRole = MurderRole.SPECTATOR;
    private int wins;
    private int losses;

    public PlayerProfile(UUID uniqueId, String name) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public String name() {
        return name;
    }

    public MurderRole lastRole() {
        return lastRole;
    }

    public void lastRole(MurderRole lastRole) {
        this.lastRole = Objects.requireNonNull(lastRole, "lastRole");
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public void recordWin() {
        wins++;
    }

    public void recordLoss() {
        losses++;
    }
}

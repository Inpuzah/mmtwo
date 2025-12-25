package com.mmhq.sharedapi.game;

import java.util.Objects;

/**
 * Lightweight server heartbeat payload for lobby/proxy coordination.
 */
public final class ServerStatus {
    private final String serverId;
    private final String presetId;
    private final String mapName;
    private final GameState state;
    private final int currentPlayers;
    private final int maxPlayers;
    private final boolean joinable;

    public ServerStatus(String serverId, String presetId, String mapName, GameState state, int currentPlayers, int maxPlayers, boolean joinable) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.presetId = Objects.requireNonNull(presetId, "presetId");
        this.mapName = Objects.requireNonNull(mapName, "mapName");
        this.state = Objects.requireNonNull(state, "state");
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.joinable = joinable;
    }

    public String serverId() { return serverId; }
    public String presetId() { return presetId; }
    public String mapName() { return mapName; }
    public GameState state() { return state; }
    public int currentPlayers() { return currentPlayers; }
    public int maxPlayers() { return maxPlayers; }
    public boolean joinable() { return joinable; }

    /**
     * Serialize to a compact string for plugin messaging. Format:
     * serverId|presetId|mapName|state|current|max|joinable
     */
    public String toPayload() {
        return serverId + "|" + presetId + "|" + mapName.replace("|", "_") + "|" + state.name() + "|" + currentPlayers + "|" + maxPlayers + "|" + (joinable ? "1" : "0");
    }

    public static ServerStatus fromPayload(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid payload: " + payload);
        }
        String serverId = parts[0];
        String presetId = parts[1];
        String mapName = parts[2];
        GameState state = GameState.valueOf(parts[3]);
        int current = Integer.parseInt(parts[4]);
        int max = Integer.parseInt(parts[5]);
        boolean joinable = "1".equals(parts[6]);
        return new ServerStatus(serverId, presetId, mapName, state, current, max, joinable);
    }
}

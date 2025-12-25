package com.mmhq.game.arena;

/**
 * Represents the state of the arena/game.
 * Transitions:
 *   IDLE -> PREPARING (when lobby sends PREPARE command)
 *   PREPARING -> WAITING (when hard reset completes)
 *   WAITING -> COUNTDOWN (when min players reached)
 *   COUNTDOWN -> PREGAME (when countdown ends)
 *   PREGAME -> IN_PROGRESS (when game starts)
 *   IN_PROGRESS -> POST_GAME (when game ends)
 *   POST_GAME -> RESETTING (cleanup phase)
 *   RESETTING -> IDLE (ready for next game)
 *   ERROR -> IDLE (after admin intervention)
 */
public enum ArenaState {
    IDLE,           // No game active, waiting for PREPARE command
    PREPARING,      // Hard reset / cloning template -> active world
    WAITING,        // Map ready, waiting for players to join
    COUNTDOWN,      // Countdown to start
    PREGAME,        // Pre-game lobby phase (players in creative/spectator)
    IN_PROGRESS,    // Active game
    POST_GAME,      // Game ended, showing results
    RESETTING,      // Hard reset after match ends
    ERROR           // Something went wrong, needs admin intervention
}

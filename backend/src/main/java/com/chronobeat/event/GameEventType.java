package com.chronobeat.event;

/** What just changed in a game. Clients treat every type the same way: re-fetch the state. */
public enum GameEventType {
    LOBBY_CHANGED,
    GAME_STARTED,
    ROUND_DEALT,
    ANSWER_LOCKED,
    ROUND_RESOLVED,
    GAME_FINISHED,
    PRESENCE_CHANGED
}

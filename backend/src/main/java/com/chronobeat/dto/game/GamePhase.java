package com.chronobeat.dto.game;

/** Which screen a given player should be looking at right now. */
public enum GamePhase {
    /** Online room not started yet. */
    LOBBY,
    /** The viewer has a round to answer. */
    ANSWERING,
    /** The viewer has answered (or it isn't their turn) and is waiting on other players. */
    WAITING,
    /** The round is resolved and on show; someone has to deal the next one. */
    REVEAL,
    FINISHED
}

package com.chronobeat.domain;

public enum RoundStatus {
    /** Dealt; the player has not answered yet. */
    PENDING,
    /**
     * The player has submitted a placement, but the round isn't resolved: in shared-song games
     * nothing about correctness may exist until every player has answered (or time ran out).
     */
    LOCKED,
    RESOLVED
}

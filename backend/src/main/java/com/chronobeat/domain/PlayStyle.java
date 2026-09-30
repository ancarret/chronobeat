package com.chronobeat.domain;

/** How rounds are dealt to the players of one game. */
public enum PlayStyle {
    /** Each turn belongs to one player, who hears a song only they place (the classic party-game rhythm). */
    TURN_BASED,
    /**
     * Every player hears the same song in the same round and places it on their own timeline.
     * Nothing is revealed until everybody has locked in, so it is a fair race on identical cards.
     */
    SHARED_SONGS
}

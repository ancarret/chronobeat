package com.chronobeat.domain;

public enum GameMode {
    SOLO,
    /** Several players on one shared device. */
    LOCAL_MULTIPLAYER,
    /** Each player on their own device, joined through a room code. */
    ONLINE_MULTIPLAYER
}

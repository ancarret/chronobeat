package com.chronobeat.domain;

/**
 * CREATED  -> game row exists with settings/players but no rounds started yet.
 * ACTIVE   -> at least one round has been started; gameplay in progress.
 * FINISHED -> ending condition reached (lives exhausted or round cap hit).
 */
public enum GameStatus {
    CREATED,
    ACTIVE,
    FINISHED
}

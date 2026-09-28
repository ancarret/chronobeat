package com.chronobeat.domain;

/**
 * Controls how the {@code SongSelectionService} spaces candidate songs
 * chronologically. We deliberately avoid fabricating a "popularity" score
 * that provider metadata cannot reliably support (see README limitations):
 * difficulty instead widens or tightens the year gap between the mystery
 * song and the player's existing timeline entries.
 */
public enum Difficulty {
    EASY,
    NORMAL,
    HARD
}

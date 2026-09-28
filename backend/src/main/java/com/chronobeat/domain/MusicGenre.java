package com.chronobeat.domain;

/**
 * Normalized genre taxonomy used across the catalog and game filters.
 * Raw provider genre strings are mapped onto this fixed set so filtering
 * stays consistent regardless of how a provider labels a track.
 */
public enum MusicGenre {
    POP,
    ROCK,
    HIP_HOP_RAP,
    ELECTRONIC,
    ALTERNATIVE_INDIE,
    METAL,
    RNB_SOUL,
    LATIN,
    REGGAETON_LATIN_URBAN,
    OTHER
}

package com.chronobeat.domain;

/**
 * Identifies which {@code MusicProvider} implementation sourced a song, so
 * additional providers (Audius, licensed catalogs, ...) can be added later
 * without breaking uniqueness of (provider, externalId) pairs.
 */
public enum MusicProviderType {
    APPLE_MUSIC
}

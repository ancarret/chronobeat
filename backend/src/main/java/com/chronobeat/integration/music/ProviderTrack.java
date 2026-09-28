package com.chronobeat.integration.music;

import java.time.LocalDate;

/** Provider-neutral shape a {@link MusicProvider} normalizes its raw responses into. */
public record ProviderTrack(
        String externalId,
        String title,
        String artist,
        String album,
        LocalDate releaseDate,
        String rawGenre,
        String market,
        String artworkUrl,
        String previewUrl,
        Integer durationMillis) {}

package com.chronobeat.integration.music.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of one entry in the iTunes Search API {@code results} array. Only fields we use are mapped. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesTrack(
        Long trackId,
        String trackName,
        String artistName,
        String collectionName,
        String releaseDate,
        String primaryGenreName,
        String artworkUrl100,
        String previewUrl,
        Integer trackTimeMillis,
        String kind) {}

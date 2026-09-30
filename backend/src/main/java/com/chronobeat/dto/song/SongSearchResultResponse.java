package com.chronobeat.dto.song;

import java.util.UUID;

/** Deliberately omits the release year - guessing it correctly is a separate part of the challenge. */
public record SongSearchResultResponse(UUID id, String title, String artist, String artworkUrl) {}

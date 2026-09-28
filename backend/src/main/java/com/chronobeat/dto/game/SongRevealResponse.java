package com.chronobeat.dto.game;

import com.chronobeat.domain.MusicGenre;
import java.util.UUID;

/** Full song metadata, only ever returned once a round has been resolved. */
public record SongRevealResponse(UUID songId, String title, String artist, String album, int year, String artworkUrl, MusicGenre genre) {}

package com.chronobeat.dto.game;

import com.chronobeat.domain.MusicGenre;
import java.util.UUID;

/** Represents an already-confirmed timeline slot, so revealing these fields never leaks a pending answer. */
public record TimelineEntryResponse(
        UUID songId, String title, String artist, String album, int year, String artworkUrl, MusicGenre genre, int position) {}

package com.chronobeat.repository;

import com.chronobeat.domain.MusicGenre;

/** How many resolved, non-anchor rounds a profile got right/wrong for one (year, genre) combination. */
public record RoundOutcomeCount(Integer year, MusicGenre genre, Boolean correct, long count) {}

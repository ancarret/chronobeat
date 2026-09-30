package com.chronobeat.repository;

/** Aggregate over a profile's played games; see {@link GamePlayerRepository#overallStats}. */
public record OverallStats(long games, int bestScore, int bestStreak, long correct, long incorrect) {}

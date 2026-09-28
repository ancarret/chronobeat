package com.chronobeat.dto.song;

public record IngestionResult(String query, int fetched, int added, int deduplicated, int filteredOut) {}

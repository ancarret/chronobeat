package com.chronobeat.dto.song;

import java.util.List;

public record IngestionSummary(int queriesProcessed, int totalAdded, long catalogSizeAfter, List<IngestionResult> results) {}

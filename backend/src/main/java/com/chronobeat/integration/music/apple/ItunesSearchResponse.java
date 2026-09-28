package com.chronobeat.integration.music.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesSearchResponse(int resultCount, List<ItunesTrack> results) {}

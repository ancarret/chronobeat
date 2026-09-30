package com.chronobeat.controller;

import com.chronobeat.dto.song.SongSearchResultResponse;
import com.chronobeat.repository.SongRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only catalog search backing the in-round "guess the song" feature.
 * Never leaks the release year (see {@link SongSearchResultResponse}) - guessing
 * it correctly is a separate part of that challenge. A minimum query length is
 * enforced so the list can't be browsed as a hint.
 */
@RestController
@Tag(name = "Songs", description = "Public catalog search used while guessing the mystery song")
public class SongController {

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_RESULTS = 8;

    private final SongRepository songRepository;

    public SongController(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @GetMapping("/api/songs/search")
    @Operation(summary = "Search the catalog by title/artist (min 3 characters); results omit the release year")
    public List<SongSearchResultResponse> search(@RequestParam(required = false) String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String trimmed = query.trim();
        return songRepository
                .findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(trimmed, trimmed, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(song -> new SongSearchResultResponse(song.getId(), song.getTitle(), song.getArtist(), song.getArtworkUrl()))
                .toList();
    }
}

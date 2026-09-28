package com.chronobeat.service;

import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.song.IngestionResult;
import com.chronobeat.dto.song.IngestionSummary;
import com.chronobeat.integration.music.MusicProvider;
import com.chronobeat.integration.music.ProviderSearchQuery;
import com.chronobeat.integration.music.ProviderTrack;
import com.chronobeat.repository.SongRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulls tracks from a {@link MusicProvider}, normalizes them into our own
 * {@link Song} catalog, and applies quality/dedup heuristics so the game never
 * has to deal with raw provider quirks (see README "Catalog quality").
 */
@Service
public class CatalogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CatalogIngestionService.class);

    /** Title substrings that reliably indicate a non-original recording we don't want in the game. */
    private static final List<String> TITLE_EXCLUSION_KEYWORDS = List.of(
            "karaoke", "tribute to", "made famous by", "in the style of", "originally performed",
            "cover version", "as made famous by");

    /**
     * Matches a trailing edition/version qualifier (remaster, live, deluxe, ...) so
     * "Wonderwall - Remastered 2014" and "Wonderwall" are recognized as the same song,
     * without touching titles that legitimately start with a parenthetical, like
     * "(Sittin' On) The Dock of the Bay" (the pattern only matches at the end).
     */
    private static final Pattern EDITION_SUFFIX = Pattern.compile(
            "(?i)[\\s]*[-(\\[]\\s*("
                    + "remaster(ed)?(\\s*\\d{4})?|deluxe(\\s*edition)?|anniversary(\\s*edition)?|"
                    + "single\\s*version|radio\\s*edit|mono(\\s*version)?|stereo(\\s*version)?|live([^)\\]]*)?|"
                    + "explicit|clean|bonus\\s*track(\\s*version)?|expanded(\\s*edition)?|extended(\\s*version)?|"
                    + "karaoke(\\s*version)?|instrumental|acoustic(\\s*version)?|demo|alternate\\s*take|edit"
                    + ")\\s*[)\\]]?\\s*$");

    private final MusicProvider musicProvider;
    private final SongRepository songRepository;
    private final GenreNormalizer genreNormalizer;

    public CatalogIngestionService(MusicProvider musicProvider, SongRepository songRepository, GenreNormalizer genreNormalizer) {
        this.musicProvider = musicProvider;
        this.songRepository = songRepository;
        this.genreNormalizer = genreNormalizer;
    }

    /**
     * Deliberately NOT {@code @Transactional}: each {@link #ingestQuery} call commits
     * independently, so one bad row (or one flaky provider response) only costs that
     * search term's results instead of rolling back everything ingested so far.
     */
    public IngestionSummary ingestSeedCatalog(String market) {
        List<IngestionResult> results = new ArrayList<>();
        for (String term : SeedCatalogQueries.DEFAULT_QUERIES) {
            try {
                results.add(ingestQuery(term, market));
            } catch (Exception ex) {
                log.warn("Skipping seed query '{}' after failure: {}", term, ex.getMessage());
            }
        }
        int totalAdded = results.stream().mapToInt(IngestionResult::added).sum();
        long catalogSize = songRepository.countByProvider(musicProvider.getType());
        log.info("Seed ingestion complete: {} queries processed, {} songs added, catalog size now {}",
                results.size(), totalAdded, catalogSize);
        return new IngestionSummary(results.size(), totalAdded, catalogSize, results);
    }

    @Transactional
    public IngestionResult ingestQuery(String term, String market) {
        List<ProviderTrack> tracks = musicProvider.search(ProviderSearchQuery.of(term, market, 50));

        int added = 0;
        int deduplicated = 0;
        int filteredOut = 0;

        for (ProviderTrack track : tracks) {
            if (isLowQuality(track)) {
                filteredOut++;
                continue;
            }
            if (songRepository.findByProviderAndExternalId(musicProvider.getType(), track.externalId()).isPresent()) {
                deduplicated++;
                continue;
            }
            Optional<Song> existingSimilar = findExistingSimilar(track);
            if (existingSimilar.isPresent()) {
                reconcileEarliestYear(existingSimilar.get(), track);
                deduplicated++;
                continue;
            }
            songRepository.save(toSong(track));
            added++;
        }

        log.debug("Ingested query '{}': fetched={} added={} deduplicated={} filteredOut={}",
                term, tracks.size(), added, deduplicated, filteredOut);
        return new IngestionResult(term, tracks.size(), added, deduplicated, filteredOut);
    }

    private boolean isLowQuality(ProviderTrack track) {
        String title = track.title().toLowerCase(Locale.ROOT);
        return TITLE_EXCLUSION_KEYWORDS.stream().anyMatch(title::contains);
    }

    private Optional<Song> findExistingSimilar(ProviderTrack track) {
        String normalizedIncoming = normalizeTitle(track.title());
        return songRepository.findByArtistIgnoreCase(track.artist()).stream()
                .filter(existing -> normalizeTitle(existing.getTitle()).equals(normalizedIncoming))
                .findFirst();
    }

    /** Prefers the earliest known release year, approximating the original release over a later reissue. */
    private void reconcileEarliestYear(Song existing, ProviderTrack duplicateTrack) {
        int duplicateYear = duplicateTrack.releaseDate().getYear();
        if (duplicateYear < existing.getEffectiveYear()) {
            existing.setCanonicalReleaseYear(duplicateYear);
        }
    }

    private static final int MAX_TEXT_FIELD_LENGTH = 500;

    private Song toSong(ProviderTrack track) {
        MusicGenre genre = genreNormalizer.normalize(track.rawGenre());
        return new Song(
                MusicProviderType.APPLE_MUSIC,
                track.externalId(),
                truncate(track.title()),
                truncate(track.artist()),
                truncate(track.album()),
                track.releaseDate(),
                track.releaseDate().getYear(),
                genre,
                track.rawGenre(),
                track.market(),
                track.artworkUrl(),
                track.previewUrl(),
                track.durationMillis());
    }

    /** Defensive guard against provider metadata occasionally exceeding our column widths. */
    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_FIELD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_FIELD_LENGTH);
    }

    static String normalizeTitle(String title) {
        String stripped = EDITION_SUFFIX.matcher(title).replaceAll("");
        return stripped.trim().toLowerCase(Locale.ROOT);
    }
}

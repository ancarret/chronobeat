package com.chronobeat.integration.music.apple;

import com.chronobeat.config.AppleMusicProperties;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.integration.music.MusicProvider;
import com.chronobeat.integration.music.ProviderSearchQuery;
import com.chronobeat.integration.music.ProviderTrack;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link MusicProvider} backed by Apple's public iTunes Search API. This is the
 * only class in the codebase that knows about iTunes' JSON shape or endpoint
 * quirks &mdash; everything upstream (catalog ingestion, the game domain) only
 * ever sees {@link ProviderTrack}.
 *
 * <p>We do not hold any special agreement with Apple: this integration uses the
 * public, unauthenticated search endpoint and only reads metadata plus the
 * short preview URL it returns. No audio is downloaded or rehosted. See the
 * README "Music provider limitation" section for licensing notes.
 */
@Component
public class AppleMusicProvider implements MusicProvider {

    private static final Logger log = LoggerFactory.getLogger(AppleMusicProvider.class);

    private final RestClient appleMusicRestClient;
    private final AppleMusicProperties properties;
    private final AtomicLong lastRequestAtMillis = new AtomicLong(0);

    public AppleMusicProvider(RestClient appleMusicRestClient, AppleMusicProperties properties) {
        this.appleMusicRestClient = appleMusicRestClient;
        this.properties = properties;
    }

    @Override
    public MusicProviderType getType() {
        return MusicProviderType.APPLE_MUSIC;
    }

    @Override
    public List<ProviderTrack> search(ProviderSearchQuery query) {
        throttle();
        String country = (query.market() == null || query.market().isBlank()) ? "US" : query.market();
        int limit = Math.min(query.limit() <= 0 ? properties.getMaxResultsPerQuery() : query.limit(), properties.getMaxResultsPerQuery());

        try {
            ItunesSearchResponse response = appleMusicRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("term", query.term())
                            .queryParam("country", country)
                            .queryParam("media", "music")
                            .queryParam("entity", "song")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(ItunesSearchResponse.class);

            if (response == null || response.results() == null) {
                log.warn("Apple Music search returned an empty body for term='{}'", query.term());
                return List.of();
            }

            return response.results().stream()
                    .map(track -> toProviderTrack(track, country))
                    .filter(java.util.Objects::nonNull)
                    .toList();

        } catch (RestClientException ex) {
            log.warn("Apple Music search failed for term='{}': {}", query.term(), ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("Unexpected error parsing Apple Music response for term='{}': {}", query.term(), ex.getMessage());
            return List.of();
        }
    }

    /** Returns null (dropped by the caller) for entries missing fields the game cannot function without. */
    private ProviderTrack toProviderTrack(ItunesTrack track, String country) {
        if (track.trackId() == null || isBlank(track.trackName()) || isBlank(track.artistName())) {
            return null;
        }
        if (isBlank(track.previewUrl())) {
            return null;
        }
        LocalDate releaseDate = parseReleaseDate(track.releaseDate());
        if (releaseDate == null) {
            return null;
        }
        return new ProviderTrack(
                track.trackId().toString(),
                track.trackName(),
                track.artistName(),
                track.collectionName(),
                releaseDate,
                track.primaryGenreName(),
                country,
                upgradeArtworkResolution(track.artworkUrl100()),
                track.previewUrl(),
                track.trackTimeMillis());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static LocalDate parseReleaseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** iTunes returns low-res 100x100 artwork by default; the higher-res asset lives at a predictable URL. */
    private static String upgradeArtworkResolution(String artworkUrl100) {
        if (isBlank(artworkUrl100)) {
            return null;
        }
        return artworkUrl100.replace("100x100bb", "600x600bb");
    }

    /** Best-effort courtesy throttle: iTunes' public endpoint has no documented rate limit or headers to honor. */
    private void throttle() {
        long minInterval = properties.getMinRequestIntervalMillis();
        if (minInterval <= 0) {
            return;
        }
        synchronized (lastRequestAtMillis) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestAtMillis.get();
            long waitMillis = minInterval - elapsed;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMillis.set(System.currentTimeMillis());
        }
    }
}

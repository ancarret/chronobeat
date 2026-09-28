package com.chronobeat.repository;

import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.Song;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/** Composable filter predicates for candidate song selection. */
public final class SongSpecifications {

    private SongSpecifications() {}

    public static Specification<Song> withMarket(String market) {
        if (market == null || market.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("market"), market);
    }

    public static Specification<Song> withGenre(MusicGenre genre) {
        if (genre == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("genre"), genre);
    }

    /** Filters on the effective year: {@code canonicalReleaseYear} when set, else {@code releaseYear}. */
    public static Specification<Song> withEffectiveYearBetween(Integer yearFrom, Integer yearTo) {
        if (yearFrom == null && yearTo == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Expression<Integer> effectiveYear =
                    cb.coalesce(root.<Integer>get("canonicalReleaseYear"), root.<Integer>get("releaseYear"));
            if (yearFrom != null && yearTo != null) {
                return cb.between(effectiveYear, yearFrom, yearTo);
            } else if (yearFrom != null) {
                return cb.greaterThanOrEqualTo(effectiveYear, yearFrom);
            } else {
                return cb.lessThanOrEqualTo(effectiveYear, yearTo);
            }
        };
    }

    public static Specification<Song> excludingIds(Collection<java.util.UUID> excludedIds) {
        if (excludedIds == null || excludedIds.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.not(root.get("id").in(excludedIds));
    }

    public static Specification<Song> excludingArtists(Collection<String> excludedArtists) {
        if (excludedArtists == null || excludedArtists.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.not(root.get("artist").in(excludedArtists));
    }

    public static Specification<Song> hasPreview() {
        return (root, query, cb) -> cb.isNotNull(root.get("previewUrl"));
    }
}

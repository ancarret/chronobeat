package com.chronobeat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A normalized catalog entry sourced from an external {@link MusicProvider}.
 * The game domain only ever reads from this table; it never talks to a
 * provider's raw response shape directly (see {@code integration.music}).
 */
@Entity
@Table(
        name = "songs",
        uniqueConstraints = @UniqueConstraint(name = "uq_songs_provider_external_id", columnNames = {"provider", "external_id"}))
public class Song {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MusicProviderType provider;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String artist;

    @Column(length = 500)
    private String album;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** Derived from {@link #releaseDate} at ingestion time; never null once persisted. */
    @Column(name = "release_year", nullable = false)
    private Integer releaseYear;

    /**
     * Optional manual override for chronology when provider metadata points at a
     * remaster/reissue rather than the original release. See README "Release date rules".
     */
    @Column(name = "canonical_release_year")
    private Integer canonicalReleaseYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MusicGenre genre;

    @Column(name = "raw_genre", length = 100)
    private String rawGenre;

    @Column(length = 10)
    private String market;

    @Column(name = "artwork_url", length = 500)
    private String artworkUrl;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "duration_millis")
    private Integer durationMillis;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Song() {
        // JPA
    }

    public Song(
            MusicProviderType provider,
            String externalId,
            String title,
            String artist,
            String album,
            LocalDate releaseDate,
            Integer releaseYear,
            MusicGenre genre,
            String rawGenre,
            String market,
            String artworkUrl,
            String previewUrl,
            Integer durationMillis) {
        this.provider = provider;
        this.externalId = externalId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.releaseDate = releaseDate;
        this.releaseYear = releaseYear;
        this.genre = genre;
        this.rawGenre = rawGenre;
        this.market = market;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.durationMillis = durationMillis;
    }

    /** The year used for chronological placement: the manual override if set, else the release year. */
    public int getEffectiveYear() {
        return canonicalReleaseYear != null ? canonicalReleaseYear : releaseYear;
    }

    public UUID getId() {
        return id;
    }

    public MusicProviderType getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public Integer getCanonicalReleaseYear() {
        return canonicalReleaseYear;
    }

    public void setCanonicalReleaseYear(Integer canonicalReleaseYear) {
        this.canonicalReleaseYear = canonicalReleaseYear;
    }

    public MusicGenre getGenre() {
        return genre;
    }

    public String getRawGenre() {
        return rawGenre;
    }

    public String getMarket() {
        return market;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public Integer getDurationMillis() {
        return durationMillis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song song)) return false;
        return id != null && id.equals(song.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

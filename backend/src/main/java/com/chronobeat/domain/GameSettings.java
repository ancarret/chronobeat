package com.chronobeat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Immutable pool/rules configuration chosen at game creation. Embedded directly
 * into {@link Game} since settings never outlive their owning game and are
 * never queried independently.
 */
@Embeddable
public class GameSettings {

    @Column(length = 10)
    private String market;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MusicGenre genre;

    @Column(name = "year_from")
    private Integer yearFrom;

    @Column(name = "year_to")
    private Integer yearTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "max_lives", nullable = false)
    private int maxLives;

    @Column(name = "max_rounds")
    private Integer maxRounds;

    protected GameSettings() {
        // JPA
    }

    public GameSettings(
            String market, MusicGenre genre, Integer yearFrom, Integer yearTo, Difficulty difficulty, int maxLives, Integer maxRounds) {
        this.market = market;
        this.genre = genre;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
        this.difficulty = difficulty;
        this.maxLives = maxLives;
        this.maxRounds = maxRounds;
    }

    /** {@code null} means "no market restriction / international". */
    public String getMarket() {
        return market;
    }

    /** {@code null} means "all genres". */
    public MusicGenre getGenre() {
        return genre;
    }

    public Integer getYearFrom() {
        return yearFrom;
    }

    public Integer getYearTo() {
        return yearTo;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getMaxLives() {
        return maxLives;
    }

    /** {@code null} means unlimited rounds (game ends only when lives are exhausted). */
    public Integer getMaxRounds() {
        return maxRounds;
    }
}

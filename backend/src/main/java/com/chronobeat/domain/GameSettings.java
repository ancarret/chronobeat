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

    @Enumerated(EnumType.STRING)
    @Column(name = "play_style", nullable = false, length = 20)
    private PlayStyle playStyle;

    @Column(name = "target_timeline_size")
    private Integer targetTimelineSize;

    @Column(name = "answer_seconds")
    private Integer answerSeconds;

    protected GameSettings() {
        // JPA
    }

    /** Classic rules: turn-based, lives-based ending, untimed. */
    public GameSettings(
            String market, MusicGenre genre, Integer yearFrom, Integer yearTo, Difficulty difficulty, int maxLives, Integer maxRounds) {
        this(market, genre, yearFrom, yearTo, difficulty, maxLives, maxRounds, PlayStyle.TURN_BASED, null, null);
    }

    public GameSettings(
            String market,
            MusicGenre genre,
            Integer yearFrom,
            Integer yearTo,
            Difficulty difficulty,
            int maxLives,
            Integer maxRounds,
            PlayStyle playStyle,
            Integer targetTimelineSize,
            Integer answerSeconds) {
        this.market = market;
        this.genre = genre;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
        this.difficulty = difficulty;
        this.maxLives = maxLives;
        this.maxRounds = maxRounds;
        this.playStyle = playStyle;
        this.targetTimelineSize = targetTimelineSize;
        this.answerSeconds = answerSeconds;
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

    public PlayStyle getPlayStyle() {
        return playStyle;
    }

    /** {@code null} means there is no race: the game ends on lives or the round cap only. */
    public Integer getTargetTimelineSize() {
        return targetTimelineSize;
    }

    /** {@code null} means untimed. */
    public Integer getAnswerSeconds() {
        return answerSeconds;
    }
}

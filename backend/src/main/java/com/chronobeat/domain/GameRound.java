package com.chronobeat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One player's turn on one mystery song. In a turn-based game a round belongs to a single
 * player; in a shared-song game every active player gets their own round on the same song
 * and the same {@link #roundNumber}.
 *
 * <p>Lifecycle: {@code PENDING -> LOCKED -> RESOLVED}. Turn-based rounds resolve the instant
 * they are answered, while shared-song rounds stay {@code LOCKED} until the whole table has
 * answered. {@link #status} plus {@link #version} (optimistic lock) guard against the same
 * round being resolved twice under concurrent/duplicate submissions.
 */
@Entity
@Table(name = "game_rounds")
public class GameRound {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_player_id", nullable = false)
    private GamePlayer gamePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    /** True when this is the player's very first round: no placement is required, see README rule #15. */
    @Column(name = "is_anchor_round", nullable = false)
    private boolean anchorRound;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoundStatus status;

    @Column(name = "submitted_position")
    private Integer submittedPosition;

    private Boolean correct;

    /** Held while LOCKED: the optional "name that song" guess is only judged at resolution. */
    @Column(name = "guessed_song_id")
    private UUID guessedSongId;

    @Column(name = "guessed_year")
    private Integer guessedYear;

    @Column(name = "guess_correct")
    private Boolean guessCorrect;

    /** Comma-separated valid insertion indices, recorded at resolution so the reveal can be replayed. */
    @Column(name = "valid_positions", length = 100)
    private String validPositions;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected GameRound() {
        // JPA
    }

    public GameRound(Game game, GamePlayer gamePlayer, Song song, int roundNumber, boolean anchorRound) {
        this.game = game;
        this.gamePlayer = gamePlayer;
        this.song = song;
        this.roundNumber = roundNumber;
        this.anchorRound = anchorRound;
        this.status = RoundStatus.PENDING;
    }

    /**
     * Records the player's answer without judging it. A {@code null} position is a timeout
     * (or, for an anchor round, simply "no decision needed").
     */
    public void lock(Integer position, UUID guessedSongId, Integer guessedYear) {
        if (status != RoundStatus.PENDING) {
            throw new IllegalStateException("Round is already " + status);
        }
        this.submittedPosition = position;
        this.guessedSongId = guessedSongId;
        this.guessedYear = guessedYear;
        this.status = RoundStatus.LOCKED;
        this.lockedAt = Instant.now();
    }

    public void resolve(boolean correct, Collection<Integer> validIndices, Boolean guessCorrect) {
        if (status == RoundStatus.RESOLVED) {
            throw new IllegalStateException("Round already resolved");
        }
        this.correct = correct;
        this.guessCorrect = guessCorrect;
        this.validPositions = validIndices.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        this.status = RoundStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public GamePlayer getGamePlayer() {
        return gamePlayer;
    }

    public Song getSong() {
        return song;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public boolean isAnchorRound() {
        return anchorRound;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public Integer getSubmittedPosition() {
        return submittedPosition;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public UUID getGuessedSongId() {
        return guessedSongId;
    }

    public Integer getGuessedYear() {
        return guessedYear;
    }

    public Boolean getGuessCorrect() {
        return guessCorrect;
    }

    /** Empty until the round is resolved. */
    public Set<Integer> getValidPositions() {
        if (validPositions == null || validPositions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(validPositions.split(",")).map(Integer::valueOf).collect(Collectors.toCollection(TreeSet::new));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameRound that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

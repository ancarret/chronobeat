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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One turn: a mystery song assigned to a specific player. {@link #status} plus
 * {@link #version} (optimistic lock) guard against the same round being
 * resolved twice under concurrent/duplicate submissions.
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

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public void resolve(Integer submittedPosition, boolean correct) {
        if (this.status == RoundStatus.RESOLVED) {
            throw new IllegalStateException("Round already resolved");
        }
        this.submittedPosition = submittedPosition;
        this.correct = correct;
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

    public Instant getCreatedAt() {
        return createdAt;
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

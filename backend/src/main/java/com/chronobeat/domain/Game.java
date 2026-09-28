package com.chronobeat.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Embedded
    private GameSettings settings;

    @Column(name = "current_round_number", nullable = false)
    private int currentRoundNumber;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("playerOrder ASC")
    private final List<GamePlayer> players = new ArrayList<>();

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Game() {
        // JPA
    }

    public Game(GameMode mode, GameSettings settings) {
        this.mode = mode;
        this.settings = settings;
        this.status = GameStatus.CREATED;
        this.currentRoundNumber = 0;
    }

    public void addPlayer(GamePlayer player) {
        players.add(player);
        player.assignToGame(this);
    }

    public void start() {
        if (status != GameStatus.CREATED) {
            throw new IllegalStateException("Game already started");
        }
        this.status = GameStatus.ACTIVE;
    }

    public void finish() {
        this.status = GameStatus.FINISHED;
    }

    public void incrementRoundNumber() {
        this.currentRoundNumber++;
    }

    /** Game ends once every player has run out of lives, or the configured round cap is reached. */
    public boolean shouldFinish() {
        boolean allEliminated = players.stream().allMatch(GamePlayer::isEliminated);
        boolean roundCapReached = settings.getMaxRounds() != null && currentRoundNumber >= settings.getMaxRounds();
        return allEliminated || roundCapReached;
    }

    public UUID getId() {
        return id;
    }

    public GameMode getMode() {
        return mode;
    }

    public GameStatus getStatus() {
        return status;
    }

    public GameSettings getSettings() {
        return settings;
    }

    public int getCurrentRoundNumber() {
        return currentRoundNumber;
    }

    public List<GamePlayer> getPlayers() {
        return players;
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
        if (!(o instanceof Game game)) return false;
        return id != null && id.equals(game.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

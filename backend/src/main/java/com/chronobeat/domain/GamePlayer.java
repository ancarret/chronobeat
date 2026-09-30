package com.chronobeat.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "game_players")
public class GamePlayer {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** SHA-256 of the secret an online player presents to act; null on shared-device games. */
    @Column(name = "token_hash", length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "is_host", nullable = false)
    private boolean host;

    /** Null for guests, i.e. players typed in by name on a shared device. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "player_order", nullable = false)
    private int playerOrder;

    @Column(nullable = false)
    private int score;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "incorrect_answers", nullable = false)
    private int incorrectAnswers;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak;

    @Column(name = "lives_remaining", nullable = false)
    private int livesRemaining;

    @OneToMany(mappedBy = "gamePlayer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private final List<TimelineEntry> timeline = new ArrayList<>();

    protected GamePlayer() {
        // JPA
    }

    public GamePlayer(String displayName, int playerOrder, int startingLives) {
        this.displayName = displayName;
        this.playerOrder = playerOrder;
        this.livesRemaining = startingLives;
    }

    void assignToGame(Game game) {
        this.game = game;
    }

    public void linkProfile(Profile profile) {
        this.profile = profile;
    }

    public void assignSeat(int playerOrder) {
        this.playerOrder = playerOrder;
    }

    public void secureWith(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void makeHost() {
        this.host = true;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isHost() {
        return host;
    }

    public Profile getProfile() {
        return profile;
    }

    public boolean isEliminated() {
        return livesRemaining <= 0;
    }

    public void loseLife() {
        this.livesRemaining = Math.max(0, livesRemaining - 1);
    }

    public void recordCorrectAnswer(int pointsAwarded) {
        this.correctAnswers++;
        this.currentStreak++;
        this.bestStreak = Math.max(bestStreak, currentStreak);
        this.score += pointsAwarded;
    }

    public void recordIncorrectAnswer() {
        this.incorrectAnswers++;
        this.currentStreak = 0;
        loseLife();
    }

    /** Awarded for correctly naming the mystery song (title/artist/year), independent of placement. */
    public void gainExtraLife() {
        this.livesRemaining++;
    }

    public void addToTimeline(TimelineEntry entry) {
        timeline.add(entry);
        entry.assignToPlayer(this);
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPlayerOrder() {
        return playerOrder;
    }

    public int getScore() {
        return score;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public int getIncorrectAnswers() {
        return incorrectAnswers;
    }

    public int getRoundsPlayed() {
        return correctAnswers + incorrectAnswers;
    }

    public double getAccuracy() {
        int played = getRoundsPlayed();
        return played == 0 ? 0.0 : (double) correctAnswers / played;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public int getLivesRemaining() {
        return livesRemaining;
    }

    public List<TimelineEntry> getTimeline() {
        return timeline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GamePlayer that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

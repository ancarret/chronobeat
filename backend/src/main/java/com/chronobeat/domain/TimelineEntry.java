package com.chronobeat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * One confirmed song in a player's personal timeline, ordered by {@link #position}.
 * Only ever created when a round is resolved as correct (or for the anchor round).
 */
@Entity
@Table(name = "timeline_entries")
public class TimelineEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_player_id", nullable = false)
    private GamePlayer gamePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    /** Zero-based chronological index within the owning player's timeline. */
    @Column(name = "position_index", nullable = false)
    private int position;

    @Column(name = "added_at_round", nullable = false)
    private int addedAtRound;

    protected TimelineEntry() {
        // JPA
    }

    public TimelineEntry(Song song, int position, int addedAtRound) {
        this.song = song;
        this.position = position;
        this.addedAtRound = addedAtRound;
    }

    void assignToPlayer(GamePlayer gamePlayer) {
        this.gamePlayer = gamePlayer;
    }

    public UUID getId() {
        return id;
    }

    public GamePlayer getGamePlayer() {
        return gamePlayer;
    }

    public Song getSong() {
        return song;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getAddedAtRound() {
        return addedAtRound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimelineEntry that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package com.chronobeat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An anonymous, account-less player identity. Owns personal records across games.
 * Only the SHA-256 hash of the secret token is stored (see {@code V2__profiles.sql}).
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
        // JPA
    }

    public Profile(String nickname, String tokenHash) {
        this.nickname = nickname;
        this.tokenHash = tokenHash;
    }

    public void rename(String nickname) {
        this.nickname = nickname;
    }

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Profile that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

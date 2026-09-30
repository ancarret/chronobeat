package com.chronobeat.repository;

import com.chronobeat.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByTokenHash(String tokenHash);
}

package com.chronobeat.repository;

import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SongRepository extends JpaRepository<Song, UUID>, JpaSpecificationExecutor<Song> {

    Optional<Song> findByProviderAndExternalId(MusicProviderType provider, String externalId);

    List<Song> findByArtistIgnoreCase(String artist);

    long countByProvider(MusicProviderType provider);
}

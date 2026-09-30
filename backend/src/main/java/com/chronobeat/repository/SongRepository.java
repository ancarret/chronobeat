package com.chronobeat.repository;

import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SongRepository extends JpaRepository<Song, UUID>, JpaSpecificationExecutor<Song> {

    Optional<Song> findByProviderAndExternalId(MusicProviderType provider, String externalId);

    List<Song> findByArtistIgnoreCase(String artist);

    long countByProvider(MusicProviderType provider);

    /** Used by the in-round "guess the song" search; caller is responsible for enforcing a minimum query length. */
    List<Song> findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(String title, String artist, Pageable pageable);
}

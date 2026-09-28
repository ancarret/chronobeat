package com.chronobeat.integration.music;

import com.chronobeat.domain.MusicProviderType;
import java.util.List;

/**
 * The only door the rest of the application has into an external music catalog.
 * {@code CatalogIngestionService} depends on this interface, never on a concrete
 * provider, so a new source (Audius, a licensed catalog, ...) can be added by
 * writing one more implementation without touching ingestion or game logic.
 */
public interface MusicProvider {

    MusicProviderType getType();

    /**
     * Returns normalized, best-effort results for the query. Implementations must
     * never throw for ordinary failure modes (timeouts, empty results, malformed
     * entries) &mdash; they should log and return whatever could be salvaged, since
     * catalog ingestion runs unattended.
     */
    List<ProviderTrack> search(ProviderSearchQuery query);
}

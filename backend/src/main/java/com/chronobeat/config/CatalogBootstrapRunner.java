package com.chronobeat.config;

import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.CatalogIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes the app playable immediately after a fresh checkout: if the catalog is
 * (nearly) empty when running locally, kicks off seed ingestion in a background
 * thread so the server doesn't block on ~90 throttled iTunes calls at startup.
 * Disabled in production &mdash; catalog growth there should be a deliberate,
 * observed operation via {@code POST /api/admin/catalog/ingest/seed}.
 */
@Component
@Profile("!prod & !test")
public class CatalogBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogBootstrapRunner.class);
    private static final long MIN_CATALOG_SIZE = 100;

    private final SongRepository songRepository;
    private final CatalogIngestionService catalogIngestionService;

    public CatalogBootstrapRunner(SongRepository songRepository, CatalogIngestionService catalogIngestionService) {
        this.songRepository = songRepository;
        this.catalogIngestionService = catalogIngestionService;
    }

    @Override
    public void run(String... args) {
        long currentSize = songRepository.countByProvider(MusicProviderType.APPLE_MUSIC);
        if (currentSize >= MIN_CATALOG_SIZE) {
            log.info("Catalog already has {} songs; skipping seed ingestion", currentSize);
            return;
        }
        log.info("Catalog has only {} songs; starting background seed ingestion from Apple Music...", currentSize);
        Thread ingestionThread = new Thread(this::runIngestionSafely, "catalog-bootstrap");
        ingestionThread.setDaemon(true);
        ingestionThread.start();
    }

    private void runIngestionSafely() {
        try {
            var summary = catalogIngestionService.ingestSeedCatalog("US");
            log.info("Background seed ingestion finished: {} songs added, catalog size now {}",
                    summary.totalAdded(), summary.catalogSizeAfter());
        } catch (Exception ex) {
            log.error("Background seed ingestion failed", ex);
        }
    }
}

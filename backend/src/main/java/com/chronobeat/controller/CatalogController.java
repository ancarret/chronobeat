package com.chronobeat.controller;

import com.chronobeat.config.AdminProperties;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.dto.song.IngestionResult;
import com.chronobeat.dto.song.IngestionSummary;
import com.chronobeat.exception.AdminAccessDeniedException;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.service.CatalogIngestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development/operator endpoints for populating the song catalog. Not meant for
 * end users: writes are gated behind a shared-secret header rather than full
 * authentication, which is an intentional MVP tradeoff (see README "Security").
 */
@RestController
@Tag(name = "Catalog admin", description = "Ingest songs from the configured music provider")
public class CatalogController {

    private final CatalogIngestionService catalogIngestionService;
    private final SongRepository songRepository;
    private final AdminProperties adminProperties;

    public CatalogController(CatalogIngestionService catalogIngestionService, SongRepository songRepository, AdminProperties adminProperties) {
        this.catalogIngestionService = catalogIngestionService;
        this.songRepository = songRepository;
        this.adminProperties = adminProperties;
    }

    @GetMapping("/api/admin/catalog/size")
    public Map<String, Long> catalogSize() {
        return Map.of("totalSongs", songRepository.countByProvider(MusicProviderType.APPLE_MUSIC));
    }

    @PostMapping("/api/admin/catalog/ingest/seed")
    public IngestionSummary ingestSeedCatalog(
            @RequestHeader("X-Admin-Key") String adminKey, @RequestParam(defaultValue = "US") String market) {
        requireAdmin(adminKey);
        return catalogIngestionService.ingestSeedCatalog(market);
    }

    @PostMapping("/api/admin/catalog/ingest")
    public IngestionResult ingestSingleQuery(
            @RequestHeader("X-Admin-Key") String adminKey,
            @RequestParam String term,
            @RequestParam(defaultValue = "US") String market) {
        requireAdmin(adminKey);
        return catalogIngestionService.ingestQuery(term, market);
    }

    private void requireAdmin(String providedKey) {
        if (providedKey == null || !providedKey.equals(adminProperties.getIngestKey())) {
            throw new AdminAccessDeniedException();
        }
    }
}

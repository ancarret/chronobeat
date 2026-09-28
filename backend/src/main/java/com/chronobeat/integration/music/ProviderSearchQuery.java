package com.chronobeat.integration.music;

/**
 * @param term free-text search term (artist name, song title, or genre keyword)
 * @param market ISO country code the search should be scoped to (provider-specific meaning)
 * @param limit maximum number of results requested
 */
public record ProviderSearchQuery(String term, String market, int limit) {

    public static ProviderSearchQuery of(String term, String market, int limit) {
        return new ProviderSearchQuery(term, market, limit);
    }
}

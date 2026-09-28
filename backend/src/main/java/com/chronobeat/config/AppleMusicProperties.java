package com.chronobeat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chronobeat.music.apple")
public class AppleMusicProperties {

    /** Base URL of the iTunes Search API. Overridable so tests can point at a mock server. */
    private String baseUrl = "https://itunes.apple.com";

    private int connectTimeoutMillis = 5000;

    private int readTimeoutMillis = 8000;

    /** Minimum delay enforced between outbound requests, as a courtesy rate limit. */
    private int minRequestIntervalMillis = 400;

    private int maxResultsPerQuery = 50;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getMinRequestIntervalMillis() {
        return minRequestIntervalMillis;
    }

    public void setMinRequestIntervalMillis(int minRequestIntervalMillis) {
        this.minRequestIntervalMillis = minRequestIntervalMillis;
    }

    public int getMaxResultsPerQuery() {
        return maxResultsPerQuery;
    }

    public void setMaxResultsPerQuery(int maxResultsPerQuery) {
        this.maxResultsPerQuery = maxResultsPerQuery;
    }
}

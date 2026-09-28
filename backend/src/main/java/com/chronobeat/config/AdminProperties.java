package com.chronobeat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chronobeat.admin")
public class AdminProperties {

    /** Shared secret required in the {@code X-Admin-Key} header for catalog-ingestion endpoints. */
    private String ingestKey = "local-dev-key";

    public String getIngestKey() {
        return ingestKey;
    }

    public void setIngestKey(String ingestKey) {
        this.ingestKey = ingestKey;
    }
}

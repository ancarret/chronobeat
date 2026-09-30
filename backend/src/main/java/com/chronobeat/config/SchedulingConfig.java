package com.chronobeat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled} sweeps; turn off with {@code chronobeat.scheduling.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "chronobeat.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}

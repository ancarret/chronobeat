package com.chronobeat.dto.profile;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(UUID id, String nickname, Instant createdAt) {}

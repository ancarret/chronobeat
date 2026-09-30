package com.chronobeat.dto.profile;

import java.util.UUID;

/**
 * Returned exactly once, at creation. {@code token} is the profile's only credential and is
 * never shown again (the server keeps just its hash), so the client must persist it.
 */
public record ProfileCreatedResponse(UUID id, String nickname, String token) {}

package com.chronobeat.event;

import java.util.UUID;

public record GameChangedEvent(UUID gameId, GameEventType type) {}

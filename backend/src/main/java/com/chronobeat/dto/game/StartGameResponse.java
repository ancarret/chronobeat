package com.chronobeat.dto.game;

public record StartGameResponse(GameResponse game, RoundPendingResponse currentRound) {}

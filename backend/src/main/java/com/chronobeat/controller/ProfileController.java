package com.chronobeat.controller;

import com.chronobeat.dto.profile.CreateProfileRequest;
import com.chronobeat.dto.profile.ProfileCreatedResponse;
import com.chronobeat.dto.profile.ProfileResponse;
import com.chronobeat.dto.profile.ProfileStatsResponse;
import com.chronobeat.dto.profile.UpdateProfileRequest;
import com.chronobeat.service.ProfileService;
import com.chronobeat.service.ProfileStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Profiles", description = "Anonymous player profiles and personal records")
public class ProfileController {

    public static final String TOKEN_HEADER = "X-Profile-Token";

    private final ProfileService profileService;
    private final ProfileStatsService profileStatsService;

    public ProfileController(ProfileService profileService, ProfileStatsService profileStatsService) {
        this.profileService = profileService;
        this.profileStatsService = profileStatsService;
    }

    @PostMapping("/api/profiles")
    @Operation(summary = "Create an anonymous profile; the secret token in the response is shown only once")
    public ResponseEntity<ProfileCreatedResponse> create(@Valid @RequestBody CreateProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.create(request.nickname()));
    }

    @GetMapping("/api/profiles/me")
    @Operation(summary = "The profile owning the given token (also used to restore a profile on a new device)")
    public ProfileResponse me(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return profileService.toResponse(profileService.authenticate(token));
    }

    @PutMapping("/api/profiles/me")
    public ProfileResponse rename(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.rename(token, request.nickname());
    }

    @GetMapping("/api/profiles/me/stats")
    @Operation(summary = "Personal records, accuracy by decade/genre and recent games")
    public ProfileStatsResponse stats(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return profileStatsService.statsFor(profileService.authenticate(token));
    }
}

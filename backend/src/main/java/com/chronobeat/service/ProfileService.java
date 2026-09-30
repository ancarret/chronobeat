package com.chronobeat.service;

import com.chronobeat.domain.Profile;
import com.chronobeat.dto.profile.ProfileCreatedResponse;
import com.chronobeat.dto.profile.ProfileResponse;
import com.chronobeat.exception.InvalidProfileTokenException;
import com.chronobeat.repository.ProfileRepository;
import com.chronobeat.util.SecretTokens;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-less identity. A profile is a nickname plus a random 256-bit secret handed to
 * the browser once; the server keeps only its SHA-256 (see {@link SecretTokens}).
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public ProfileCreatedResponse create(String nickname) {
        String token = SecretTokens.generate();
        Profile profile = profileRepository.saveAndFlush(new Profile(nickname.trim(), SecretTokens.hash(token)));
        return new ProfileCreatedResponse(profile.getId(), profile.getNickname(), token);
    }

    /** Resolves the profile for a token, or empty when the token is absent/unknown (i.e. play as a guest). */
    @Transactional(readOnly = true)
    public Optional<Profile> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return profileRepository.findByTokenHash(SecretTokens.hash(token.trim()));
    }

    @Transactional(readOnly = true)
    public Profile authenticate(String token) {
        return findByToken(token).orElseThrow(InvalidProfileTokenException::new);
    }

    @Transactional
    public ProfileResponse rename(String token, String nickname) {
        Profile profile = authenticate(token);
        profile.rename(nickname.trim());
        return toResponse(profileRepository.saveAndFlush(profile));
    }

    public ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(profile.getId(), profile.getNickname(), profile.getCreatedAt());
    }
}

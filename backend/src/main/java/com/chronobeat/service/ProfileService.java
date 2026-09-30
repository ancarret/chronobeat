package com.chronobeat.service;

import com.chronobeat.domain.Profile;
import com.chronobeat.dto.profile.ProfileCreatedResponse;
import com.chronobeat.dto.profile.ProfileResponse;
import com.chronobeat.exception.InvalidProfileTokenException;
import com.chronobeat.repository.ProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-less identity. A profile is a nickname plus a random 256-bit secret handed to
 * the browser once; the server keeps only the SHA-256 of it. Because the token is
 * high-entropy there is nothing to brute-force, so a plain fast hash (no salt or stretching)
 * is the right tool here, unlike for human-chosen passwords.
 */
@Service
public class ProfileService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public ProfileCreatedResponse create(String nickname) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Profile profile = profileRepository.saveAndFlush(new Profile(nickname.trim(), hash(token)));
        return new ProfileCreatedResponse(profile.getId(), profile.getNickname(), token);
    }

    /** Resolves the profile for a token, or empty when the token is absent/unknown (i.e. play as a guest). */
    @Transactional(readOnly = true)
    public Optional<Profile> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return profileRepository.findByTokenHash(hash(token.trim()));
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

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}

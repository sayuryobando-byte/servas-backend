package com.servas.application.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenService {

    private static final String TOKEN_KEY_PREFIX = "session:token:";
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final StringRedisTemplate redis;

    public String issue(UUID userId) {
        var token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_KEY_PREFIX + token, userId.toString(), SESSION_TTL);
        return token;
    }

    public Optional<UUID> resolve(String token) {
        return Optional.ofNullable(redis.opsForValue().get(TOKEN_KEY_PREFIX + token))
            .flatMap(this::parse);
    }

    public void revoke(String token) {
        redis.delete(TOKEN_KEY_PREFIX + token);
    }

    private Optional<UUID> parse(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
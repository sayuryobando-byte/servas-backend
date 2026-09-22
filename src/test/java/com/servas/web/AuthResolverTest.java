package com.servas.web;

import com.servas.application.auth.AuthService;
import com.servas.application.auth.AuthService.LoginCommand;
import com.servas.application.auth.TokenService;
import com.servas.common.constant.Errors;
import com.servas.domain.entity.User;
import com.servas.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResolverTest extends IntegrationTestBase {

    @Autowired
    private AuthResolver authResolver;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private AuthService authService;

    @Test
    void requireUserIdHandlesEveryTokenShape() {
        assertThat(authResolver.requireUserId(null).error().code())
            .isEqualTo(Errors.AUTH_REQUIRED.code());
        assertThat(authResolver.requireUserId("Bearer    ").error().code())
            .isEqualTo(Errors.AUTH_REQUIRED.code());
        assertThat(authResolver.requireUserId("Basic abc").error().code())
            .isEqualTo(Errors.AUTH_REQUIRED.code());
        assertThat(authResolver.requireUserId("Bearer token-roto").error().code())
            .isEqualTo(Errors.TOKEN_INVALID.code());

        var user = persistUser();
        var token = tokenService.issue(user.getId());
        var resolved = authResolver.requireUserId("Bearer " + token);
        assertThat(resolved.isSuccess()).isTrue();
        assertThat(resolved.valueUnchecked()).isEqualTo(user.getId());
    }

    @Test
    void requireProviderRejectsUserWithoutProvider() {
        var user = persistUser();
        var token = tokenService.issue(user.getId());

        var outcome = authResolver.requireProvider("Bearer " + token);
        assertThat(outcome.isFailure()).isTrue();
        assertThat(outcome.error().code()).isEqualTo(Errors.PROVIDER_NOT_FOUND.code());
    }

    @Test
    void tokenServiceResolvesStoredUserIdAndRejectsGarbage() {
        var user = persistUser();
        var token = tokenService.issue(user.getId());
        assertThat(tokenService.resolve(token)).contains(user.getId());

        redis.opsForValue().set("session:token:basura", "no-es-un-uuid");
        assertThat(tokenService.resolve("basura")).isEmpty();

        redis.opsForValue().set("session:token:valido", user.getId().toString());
        assertThat(tokenService.resolve("valido")).contains(user.getId());
    }

    @Test
    void tokenOfOnlyAcceptsNonEmptyBearer() {
        assertThat(authResolver.tokenOf(null)).isEmpty();
        assertThat(authResolver.tokenOf("Bearer    ")).isEmpty();
        assertThat(authResolver.tokenOf("Basic abc")).isEmpty();
        assertThat(authResolver.tokenOf("Bearer token-valido")).contains("token-valido");
        assertThat(authResolver.tokenOf("Bearer X Y")).contains("X Y");
    }

    @Test
    void loginWithUnknownUserFailsWithInvalidCredentials() {
        var outcome = authService.login(new LoginCommand(unique("ghost") + "@servas.test", "Pass1234!"));
        assertThat(outcome.isFailure()).isTrue();
        assertThat(outcome.error().code()).isEqualTo(Errors.INVALID_CREDENTIALS.code());
    }

    @Test
    void persistedUserKeepsProvidedCreatedAt() {
        var createdAt = Instant.ofEpochMilli(1_000L);
        var user = users.save(User.builder()
            .email(unique("pre") + "@servas.test")
            .passwordHash(passwordEncoder.encode("Pass1234!"))
            .isVerified(false)
            .createdAt(createdAt)
            .build());
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }
}
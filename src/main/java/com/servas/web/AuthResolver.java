package com.servas.web;

import com.servas.application.auth.TokenService;
import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Provider;
import com.servas.domain.repository.ProviderRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ProviderRepository providerRepository;

    public Result<Provider> requireProvider(String authorizationHeader) {
        var token = tokenOf(authorizationHeader).orElse(null);
        if (token == null) {
            return Result.failure(Errors.AUTH_REQUIRED);
        }
        var userId = tokenService.resolve(token).orElse(null);
        if (userId == null) {
            return Result.failure(Errors.TOKEN_INVALID);
        }
        return providerRepository.findByUserId(userId)
            .map(Result::value)
            .orElse(Result.failure(Errors.PROVIDER_NOT_FOUND));
    }

    public Optional<String> tokenOf(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(extract(authorizationHeader));
    }

    public Result<UUID> requireUserId(String authorizationHeader) {
        var token = tokenOf(authorizationHeader).orElse(null);
        if (token == null) {
            return Result.failure(Errors.AUTH_REQUIRED);
        }
        return tokenService.resolve(token)
            .map(Result::value)
            .orElse(Result.failure(Errors.TOKEN_INVALID));
    }

    private String extract(String header) {
        var raw = header.substring(BEARER_PREFIX.length()).trim();
        return raw.isEmpty() ? null : raw;
    }
}
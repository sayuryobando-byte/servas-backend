package com.servas.application.auth;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Provider;
import com.servas.domain.entity.User;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.repository.ProviderRepository;
import com.servas.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;

    @Transactional
    public Result<User> registerProvider(RegisterProviderCommand command) {
        if (userRepository.existsByEmail(normalizeEmail(command.email()))) {
            return Result.failure(Errors.EMAIL_TAKEN);
        }
        var user = userRepository.save(User.builder()
            .email(normalizeEmail(command.email()))
            .passwordHash(passwordEncoder.encode(command.password()))
            .isVerified(Boolean.FALSE)
            .build());
        providerRepository.save(Provider.builder()
            .user(user)
            .documentType(command.documentType())
            .documentNumber(command.documentNumber())
            .firstName(command.firstName())
            .lastName(command.lastName())
            .birthDate(command.birthDate())
            .phone(command.phone())
            .build());
        otpService.issue(user.getEmail());
        return Result.value(user);
    }

    @Transactional
    public Result<User> verifyEmail(String email, String otpCode) {
        var normalized = normalizeEmail(email);
        return otpService.validate(normalized, otpCode)
            .flatMap(ignored -> userRepository.findByEmail(normalized)
                .map(user -> {
                    user.markVerified();
                    return Result.value(userRepository.save(user));
                })
                .orElse(Result.failure(Errors.USER_NOT_FOUND)));
    }

    @Transactional
    public Result<Void> resendVerification(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
            .map(user -> {
                otpService.issue(user.getEmail());
                return Result.<Void>value(null);
            })
            .orElse(Result.failure(Errors.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Result<SessionInfo> login(LoginCommand command) {
        var user = userRepository.findByEmail(normalizeEmail(command.email())).orElse(null);
        if (user == null || !passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            return Result.failure(Errors.INVALID_CREDENTIALS);
        }
        if (!user.isVerified()) {
            return Result.failure(Errors.EMAIL_NOT_VERIFIED);
        }
        return Result.value(new SessionInfo(tokenService.issue(user.getId()), user.getId(), user.getEmail()));
    }

    @Transactional
    public Result<Void> logout(String token) {
        tokenService.revoke(token);
        return Result.value(null);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record RegisterProviderCommand(String email, String password, DocumentType documentType,
                                          String documentNumber, String firstName, String lastName,
                                          LocalDate birthDate, String phone) {
    }

    public record LoginCommand(String email, String password) {
    }

    public record SessionInfo(String token, UUID userId, String email) {
    }
}
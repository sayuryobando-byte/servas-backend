package com.servas.web.auth;

import com.servas.application.auth.AuthService;
import com.servas.application.auth.AuthService.LoginCommand;
import com.servas.application.auth.AuthService.RegisterProviderCommand;
import com.servas.domain.enumeration.DocumentType;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthResolver authResolver;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterProviderRequest request) {
        var result = authService.registerProvider(new RegisterProviderCommand(
            request.email(),
            request.password(),
            request.documentType(),
            request.documentNumber(),
            request.firstName(),
            request.lastName(),
            request.birthDate(),
            request.phone()));
        return Api.resolve(result.map(user -> new RegisterResponse(user.getEmail())), HttpStatus.CREATED);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verify(@RequestBody VerifyEmailRequest request) {
        var result = authService.verifyEmail(request.email(), request.otpCode());
        return Api.resolve(result.map(user -> new VerifyResponse(user.getEmail())), HttpStatus.OK);
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<?> resend(@RequestBody ResendRequest request) {
        return Api.resolve(authService.resendVerification(request.email()), HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return Api.resolve(authService.login(new LoginCommand(request.email(), request.password())), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var token = authResolver.tokenOf(authorization).orElse(null);
        if (token == null) {
            return Api.resolve(authService.logout(null), HttpStatus.OK);
        }
        return Api.resolve(authService.logout(token), HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@RequestBody ResendRequest request) {
        return Api.resolve(authService.resendVerification(request.email()), HttpStatus.OK);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterProviderRequest(String email, String password, DocumentType documentType,
                                          String documentNumber, String firstName, String lastName,
                                          LocalDate birthDate, String phone) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VerifyEmailRequest(String email, String otpCode) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResendRequest(String email) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginRequest(String email, String password) {
    }

    public record RegisterResponse(String email) {
    }

    public record VerifyResponse(String email) {
    }
}
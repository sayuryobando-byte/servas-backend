package com.servas.web;

import com.servas.common.functional.AppError;
import com.servas.common.functional.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class Api {

    private Api() {
    }

    public record ApiError(String code, String message) {
    }

    public static <T> ResponseEntity<?> resolve(Result<T> result, HttpStatus success) {
        return result.isSuccess()
            ? ResponseEntity.status(success).body(result.valueUnchecked())
            : ResponseEntity.status(statusOf(result.error()))
            .body(new ApiError(result.error().code(), result.error().message()));
    }

    public static HttpStatus statusOf(AppError error) {
        return switch (error.code()) {
            case "INVALID_CREDENTIALS", "AUTH_REQUIRED", "TOKEN_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "EMAIL_NOT_VERIFIED", "NOT_COMPANY_OWNER" -> HttpStatus.FORBIDDEN;
            case "EMAIL_TAKEN", "NIT_TAKEN", "SLOT_NOT_AVAILABLE" -> HttpStatus.CONFLICT;
            case "USER_NOT_FOUND", "PROVIDER_NOT_FOUND", "COMPANY_NOT_FOUND", "SERVICE_NOT_FOUND",
                 "COMUNA_NOT_FOUND", "RESERVATION_NOT_FOUND", "CLIENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
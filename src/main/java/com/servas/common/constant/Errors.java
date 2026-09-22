package com.servas.common.constant;

import com.servas.common.functional.AppError;

public final class Errors {

    private Errors() {
    }

    public static final AppError EMAIL_TAKEN = AppError.of("EMAIL_TAKEN", "El correo ya se encuentra registrado.");
    public static final AppError INVALID_CREDENTIALS = AppError.of("INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    public static final AppError EMAIL_NOT_VERIFIED = AppError.of("EMAIL_NOT_VERIFIED", "Debe verificar su correo antes de iniciar sesión.");
    public static final AppError INVALID_OTP = AppError.of("INVALID_OTP", "El código es inválido o ha expirado.");
    public static final AppError OTP_ATTEMPTS_EXCEEDED = AppError.of("OTP_ATTEMPTS_EXCEEDED", "Superó el máximo de 3 intentos.");
    public static final AppError USER_NOT_FOUND = AppError.of("USER_NOT_FOUND", "Usuario no encontrado.");
    public static final AppError AUTH_REQUIRED = AppError.of("AUTH_REQUIRED", "Se requiere autenticación.");
    public static final AppError TOKEN_INVALID = AppError.of("TOKEN_INVALID", "El token de sesión es inválido o expiró.");

    public static final AppError PROVIDER_NOT_FOUND = AppError.of("PROVIDER_NOT_FOUND", "Proveedor no encontrado.");
    public static final AppError COMPANY_NOT_FOUND = AppError.of("COMPANY_NOT_FOUND", "Empresa no encontrada.");
    public static final AppError NIT_TAKEN = AppError.of("NIT_TAKEN", "El NIT ya se encuentra registrado.");
    public static final AppError NOT_COMPANY_OWNER = AppError.of("NOT_COMPANY_OWNER", "La empresa no pertenece al proveedor.");

    public static final AppError SERVICE_NOT_FOUND = AppError.of("SERVICE_NOT_FOUND", "Servicio no encontrado.");
    public static final AppError SERVICE_INACTIVE = AppError.of("SERVICE_INACTIVE", "El servicio se encuentra inactivo.");
    public static final AppError SERVICE_NOT_IN_COMPANY = AppError.of("SERVICE_NOT_IN_COMPANY", "El servicio no pertenece a la empresa.");
    public static final AppError COMUNA_REQUIRED = AppError.of("COMUNA_REQUIRED", "La comuna es obligatoria para modalidad presencial.");
    public static final AppError COMUNA_NOT_FOUND = AppError.of("COMUNA_NOT_FOUND", "Comuna no encontrada.");

    public static final AppError INVALID_SCHEDULE = AppError.of("INVALID_SCHEDULE", "Los horarios son inválidos o se superponen.");
    public static final AppError SERVICE_WITHOUT_SCHEDULE = AppError.of("SERVICE_WITHOUT_SCHEDULE", "El servicio no atiende en esa fecha.");
    public static final AppError DATE_OUT_OF_RANGE = AppError.of("DATE_OUT_OF_RANGE", "La fecha está fuera del rango de atención.");
    public static final AppError DATE_BLOCKED = AppError.of("DATE_BLOCKED", "La fecha se encuentra bloqueada.");
    public static final AppError OUTSIDE_ATTENTION_HOURS = AppError.of("OUTSIDE_ATTENTION_HOURS", "La franja está fuera del horario de atención.");
    public static final AppError END_TIME_MISMATCH = AppError.of("END_TIME_MISMATCH", "El fin de la reserva no coincide con la duración del servicio.");
    public static final AppError SLOT_NOT_AVAILABLE = AppError.of("SLOT_NOT_AVAILABLE", "La franja ya no está disponible.");
    public static final AppError RESERVATION_NOT_FOUND = AppError.of("RESERVATION_NOT_FOUND", "Reserva no encontrada.");
    public static final AppError RESERVATION_NOT_ACTIVE = AppError.of("RESERVATION_NOT_ACTIVE", "La reserva no admite cambios.");
    public static final AppError CLIENT_NOT_FOUND = AppError.of("CLIENT_NOT_FOUND", "Cliente no encontrado.");
}
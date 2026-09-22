package com.servas.common.functional;

public record AppError(String code, String message) {

    public static AppError of(String code, String message) {
        return new AppError(code, message);
    }
}
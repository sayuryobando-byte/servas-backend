package com.servas.application.auth;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpService {

    private static final String OTP_KEY_PREFIX = "otp:email:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp:attempts:email:";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 3;

    private final StringRedisTemplate redis;

    public String issue(String email) {
        var key = otpKey(email);
        redis.delete(attemptsKey(email));
        var code = String.format(Locale.ROOT, "%0" + OTP_LENGTH + "d",
            ThreadLocalRandom.current().nextInt((int) Math.pow(10, OTP_LENGTH)));
        redis.opsForValue().set(key, code, OTP_TTL);
        return code;
    }

    public Result<Void> validate(String email, String code) {
        var otpKey = otpKey(email);
        var attemptsKey = attemptsKey(email);
        var stored = redis.opsForValue().get(otpKey);
        if (stored == null) {
            return Result.failure(Errors.INVALID_OTP);
        }
        if (stored.equals(code)) {
            redis.delete(otpKey);
            redis.delete(attemptsKey);
            return Result.value(null);
        }
        var attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts == 1) {
            redis.expire(attemptsKey, OTP_TTL);
        }
        if (attempts >= MAX_ATTEMPTS) {
            redis.delete(otpKey);
            return Result.failure(Errors.OTP_ATTEMPTS_EXCEEDED);
        }
        return Result.failure(Errors.INVALID_OTP);
    }

    private String otpKey(String email) {
        return OTP_KEY_PREFIX + email;
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + email;
    }
}
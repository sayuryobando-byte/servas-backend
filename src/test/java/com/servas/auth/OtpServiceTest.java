package com.servas.auth;

import com.servas.application.auth.OtpService;
import com.servas.common.constant.Errors;
import com.servas.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OtpServiceTest extends IntegrationTestBase {

    @Autowired
    private OtpService otpService;

    @Test
    void rejectsCodeWithNoIssuedOtp() {
        assertThat(otpService.validate(unique("ghost") + "@servas.test", "123456").error().code())
            .isEqualTo(Errors.INVALID_OTP.code());
    }

    @Test
    void wrongAttemptsPendingThenCorrectCodeSucceeds() {
        var email = unique("otp") + "@servas.test";
        var code = otpService.issue(email);

        assertThat(otpService.validate(email, otherWorth(code)).error().code())
            .isEqualTo(Errors.INVALID_OTP.code());
        assertThat(otpService.validate(email, code).isSuccess()).isTrue();
    }

    @Test
    void exhaustingAttemptsDeletesOtp() {
        var email = unique("otp") + "@servas.test";
        var code = otpService.issue(email);

        var wrongOne = otherWorth(code);
        var wrongTwo = otherWorth(wrongOne);
        assertThat(otpService.validate(email, wrongOne).error().code()).isEqualTo(Errors.INVALID_OTP.code());
        assertThat(otpService.validate(email, wrongTwo).error().code()).isEqualTo(Errors.INVALID_OTP.code());
        assertThat(otpService.validate(email, wrongOne).error().code())
            .isEqualTo(Errors.OTP_ATTEMPTS_EXCEEDED.code());
        assertThat(otpService.validate(email, code).error().code())
            .isEqualTo(Errors.INVALID_OTP.code());
    }

    private String otherWorth(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }
}
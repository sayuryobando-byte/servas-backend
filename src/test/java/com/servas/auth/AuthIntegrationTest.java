package com.servas.auth;

import com.servas.application.auth.AuthService;
import com.servas.application.auth.AuthService.LoginCommand;
import com.servas.application.auth.AuthService.RegisterProviderCommand;
import com.servas.application.auth.TokenService;
import com.servas.common.constant.Errors;
import com.servas.domain.enumeration.DocumentType;
import com.servas.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private TokenService tokenService;

    @Test
    void registerVerifyLoginAndLogoutFlow() {
        var email = unique("ea") + "@demo.servas";

        var register = authService.registerProvider(command(email));
        assertThat(register.isSuccess()).isTrue();

        var code = otpOf(email);
        var verify = authService.verifyEmail(email, code);
        assertThat(verify.isSuccess()).isTrue();

        var session = authService.login(new LoginCommand(email, "Pass1234!"));
        assertThat(session.isSuccess()).isTrue();
        var token = session.valueUnchecked().token();

        assertThat(authService.logout(token).isSuccess()).isTrue();
        assertThat(tokenService.resolve(token)).isEmpty();
    }

    @Test
    void rejectsDuplicateEmail() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));
        var result = authService.registerProvider(command(email));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.EMAIL_TAKEN.code());
    }

    @Test
    void rejectsInvalidOtp() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));
        var result = authService.verifyEmail(email, "000000");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.INVALID_OTP.code());
    }

    @Test
    void rejectsLoginBeforeVerification() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));
        var result = authService.login(new LoginCommand(email, "Pass1234!"));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.EMAIL_NOT_VERIFIED.code());
    }

    @Test
    void rejectsInvalidCredentials() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));
        authService.verifyEmail(email, otpOf(email));
        var result = authService.login(new LoginCommand(email, "wrong-password"));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.INVALID_CREDENTIALS.code());
    }

    @Test
    void resendVerificationIssuesNewOtpAndEnablesVerification() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));

        var firstOtp = otpOf(email);
        assertThat(authService.resendVerification(email).isSuccess()).isTrue();

        var resendOtp = otpOf(email);
        assertThat(resendOtp).isNotBlank().isNotEqualTo(firstOtp);

        assertThat(authService.verifyEmail(email, resendOtp).isSuccess()).isTrue();
        assertThat(authService.login(new LoginCommand(email, "Pass1234!")).isSuccess()).isTrue();
    }

    @Test
    void resendAndForgotPasswordRejectUnknownEmail() {
        var email = unique("ea") + "@demo.servas";
        authService.registerProvider(command(email));
        authService.verifyEmail(email, otpOf(email));

        var resend = authService.resendVerification("nadie-" + unique("x") + "@demo.servas");
        assertThat(resend.isFailure()).isTrue();
        assertThat(resend.error().code()).isEqualTo(Errors.USER_NOT_FOUND.code());
    }

    private RegisterProviderCommand command(String email) {
        return new RegisterProviderCommand(email, "Pass1234!", DocumentType.CC,
            "DOC-" + unique("d"), "Juan", "Perez", null, "3000000000");
    }
}
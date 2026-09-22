package com.servas.common.functional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    private static final AppError ERROR = AppError.of("TEST_ERROR", "Mensaje de prueba");

    @Test
    void successAndFailureSemantics() {
        var ok = Result.value("x");
        var ko = Result.<String>failure(ERROR);

        assertThat(ok.isSuccess()).isTrue();
        assertThat(ok.isFailure()).isFalse();
        assertThat(ok.valueUnchecked()).isEqualTo("x");
        assertThat(ok.error()).isNull();

        assertThat(ko.isSuccess()).isFalse();
        assertThat(ko.isFailure()).isTrue();
        assertThat(ko.error()).isEqualTo(ERROR);
        assertThat(ko.valueUnchecked()).isNull();
    }

    @Test
    void valueOrDefaultAndThrow() {
        var ok = Result.value("x");
        var ko = Result.<String>failure(ERROR);

        assertThat(ok.valueOrElse("z")).isEqualTo("x");
        assertThat(ko.valueOrElse("z")).isEqualTo("z");
        assertThat(ok.valueOrThrow()).isEqualTo("x");
        assertThatThrownBy(ko::valueOrThrow)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mensaje de prueba");
    }

    @Test
    void mapAndFlatMapOnlyRunOnSuccess() {
        var ok = Result.value("x");
        var ko = Result.<String>failure(ERROR);

        assertThat(ok.map(String::length).valueUnchecked()).isEqualTo(1);
        assertThat(ko.map(String::length).isFailure()).isTrue();
        assertThat(ok.flatMap(v -> Result.value(v + "!")).valueUnchecked()).isEqualTo("x!");
        assertThat(ko.flatMap(v -> Result.value(v)).error()).isEqualTo(ERROR);
    }

    @Test
    void peekAndOnErrorAreSideEffectsOnly() {
        var ok = Result.value("x");
        var ko = Result.<String>failure(ERROR);

        var visited = new StringBuilder();
        ok.peek(visited::append);
        ko.peek(v -> {
            throw new AssertionError("no debería ejecutarse");
        });
        assertThat(visited.toString()).isEqualTo("x");

        var errorSeen = new StringBuilder();
        ko.onError(e -> errorSeen.append(e.code()));
        ok.onError(e -> {
            throw new AssertionError("no debería ejecutarse");
        });
        assertThat(errorSeen.toString()).isEqualTo("TEST_ERROR");
    }
}
package com.servas.common.functional;

import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface Result<VALUE> permits Result.Success, Result.Failure {

    static <T> Result<T> value(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(AppError error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    boolean isFailure();

    AppError error();

    VALUE valueUnchecked();

    default VALUE valueOrElse(VALUE fallback) {
        return isSuccess() ? valueUnchecked() : fallback;
    }

    default VALUE valueOrThrow() {
        if (isSuccess()) {
            return valueUnchecked();
        }
        throw new IllegalStateException(error().message());
    }

    default <TARGET> Result<TARGET> map(Function<? super VALUE, ? extends TARGET> mapper) {
        return isSuccess() ? Result.value(mapper.apply(valueUnchecked())) : Result.failure(error());
    }

    default <TARGET> Result<TARGET> flatMap(Function<? super VALUE, Result<TARGET>> mapper) {
        return isSuccess() ? mapper.apply(valueUnchecked()) : Result.failure(error());
    }

    default void peek(Consumer<VALUE> consumer) {
        if (isSuccess()) {
            consumer.accept(valueUnchecked());
        }
    }

    default void onError(Consumer<AppError> consumer) {
        if (isFailure()) {
            consumer.accept(error());
        }
    }

    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isFailure() {
            return false;
        }

        @Override
        public T valueUnchecked() {
            return value;
        }

        @Override
        public AppError error() {
            return null;
        }
    }

    record Failure<T>(AppError error) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isFailure() {
            return true;
        }

        @Override
        public T valueUnchecked() {
            return null;
        }
    }
}
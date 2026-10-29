package com.bingang.common.exception;

public record ValidationError(String field, String message) {
}

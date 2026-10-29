package com.bingang.common.exception.error;

import java.util.List;

public record ErrorResponse(int status, String code, String message, List<ValidationError> errors) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<ValidationError> errors) {
		return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(),
				List.copyOf(errors));
	}

}

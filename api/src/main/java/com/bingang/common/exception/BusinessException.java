package com.bingang.common.exception;

import java.util.List;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final List<ValidationError> errors;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, List.of());
	}

	// 여러 입력값 중 어느 것이 문제인지 알려줄 때 쓴다
	public BusinessException(ErrorCode errorCode, List<ValidationError> errors) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.errors = errors;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public List<ValidationError> getErrors() {
		return errors;
	}

}

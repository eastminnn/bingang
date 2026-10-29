package com.bingang.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.bingang.common.exception.error.ErrorCode;
import com.bingang.common.exception.error.ErrorResponse;
import com.bingang.common.exception.error.ValidationError;

@RestControllerAdvice
public class 	GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
		return toResponse(e.getErrorCode(), e.getErrors());
	}

	// @Valid가 붙은 객체 검증 실패
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
		List<ValidationError> errors = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(this::toValidationError)
			.toList();
		return toResponse(ErrorCode.INVALID_INPUT_VALUE, errors);
	}

	// @RequestParam, @PathVariable 검증 실패
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e) {
		List<ValidationError> errors = e.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors().stream().map(error -> toValidationError(result, error)))
			.toList();
		return toResponse(ErrorCode.INVALID_INPUT_VALUE, errors);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
		return toResponse(ErrorCode.MISSING_REQUEST_PARAMETER,
				List.of(new ValidationError(e.getParameterName(), "필수 값입니다.")));
	}

	// 예: at=abc 처럼 시각으로 바꿀 수 없는 값
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return toResponse(ErrorCode.INVALID_PARAMETER_TYPE,
				List.of(new ValidationError(e.getName(), "형식이 올바르지 않습니다.")));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
		return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
	}

	// 내부 메시지는 로그에만 남기고 응답에는 공통 문구만 보낸다
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
	}

	private ValidationError toValidationError(FieldError error) {
		return new ValidationError(error.getField(), error.getDefaultMessage());
	}

	private ValidationError toValidationError(ParameterValidationResult result, MessageSourceResolvable error) {
		return new ValidationError(result.getMethodParameter().getParameterName(), error.getDefaultMessage());
	}

	private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
		return toResponse(errorCode, List.of());
	}

	private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, List<ValidationError> errors) {
		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, errors));
	}

}

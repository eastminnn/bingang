package com.bingang.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

// 필터 등 컨트롤러 밖에서 난 에러가 /error로 넘어오면 여기서 공통 형식으로 응답한다
@RestController
public class ErrorResponseController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ErrorResponseController.class);

	@RequestMapping("/error")
	public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
		HttpStatus status = resolveStatus(request);
		ErrorCode errorCode = toErrorCode(status);

		if (status.is5xxServerError()) {
			Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
			log.error("Error outside controller: status={}", status.value(),
					exception instanceof Throwable throwable ? throwable : null);
		}
		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
	}

	private HttpStatus resolveStatus(HttpServletRequest request) {
		Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		HttpStatus status = statusCode instanceof Integer code ? HttpStatus.resolve(code) : null;
		return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private ErrorCode toErrorCode(HttpStatus status) {
		return switch (status) {
			case BAD_REQUEST -> ErrorCode.INVALID_INPUT_VALUE;
			case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
			case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
			default -> ErrorCode.INTERNAL_SERVER_ERROR;
		};
	}

}

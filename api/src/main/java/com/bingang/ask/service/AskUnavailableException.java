package com.bingang.ask.service;

/** AI 호출 실패(시간 초과, 응답 형식 오류 등). 원인은 로그에만 남긴다 */
public class AskUnavailableException extends RuntimeException {

	public AskUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}

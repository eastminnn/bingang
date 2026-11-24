package com.bingang.ask.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bingang.ask.dto.AskRequest;
import com.bingang.ask.dto.AskResponse;
import com.bingang.ask.service.AskService;

@RestController
@RequestMapping("/api/ask")
@RequiredArgsConstructor
public class AskController {

	private final AskService askService;

	@PostMapping
	public AskResponse ask(@Valid @RequestBody AskRequest request, HttpServletRequest http) {
		return AskResponse.from(askService.ask(request.question(), clientOf(http)));
	}

	// Caddy 뒤에 있으므로 X-Forwarded-For의 첫 값이 실제 클라이언트. 없으면(로컬 직접 호출) 연결 주소
	// Caddy는 신뢰하지 않는 클라이언트가 보낸 X-Forwarded-For를 덮어쓰므로 사용자가 값을 바꿔 한도를 피할 수 없다
	static String clientOf(HttpServletRequest http) {
		String forwarded = http.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return http.getRemoteAddr();
	}

}

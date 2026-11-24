package com.bingang.ask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 100, message = "100자 이내로 입력해 주세요.")
        String question) {
}

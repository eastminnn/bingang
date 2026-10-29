package com.bingang.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.validation.constraints.Min;

// 테스트 클래스 안의 컨트롤러는 스캔에서 빠지므로 직접 등록한다
@WebMvcTest(controllers = ErrorResponseController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void 비즈니스_예외는_ErrorCode대로_응답한다() throws Exception {
		mvc.perform(get("/test/business"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()))
			.andExpect(jsonPath("$.errors[0].field").value("from"));
	}

	@Test
	void 필수_파라미터가_없으면_400() throws Exception {
		mvc.perform(get("/test/at"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
			.andExpect(jsonPath("$.errors[0].field").value("at"));
	}

	@Test
	void 시각_형식이_틀리면_400() throws Exception {
		mvc.perform(get("/test/at").param("at", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
			.andExpect(jsonPath("$.errors[0].field").value("at"));
	}

	@Test
	void 파라미터_검증에_실패하면_400과_필드를_알려준다() throws Exception {
		mvc.perform(get("/test/size").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
			.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	void 지원하지_않는_메서드는_405() throws Exception {
		mvc.perform(post("/test/business"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 없는_경로는_404() throws Exception {
		mvc.perform(get("/nope"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void 처리하지_않은_예외는_500이고_내부_메시지를_숨긴다() throws Exception {
		mvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
			.andExpect(content().string(not(containsString("db password"))));
	}

	@Test
	void 컨트롤러_밖_에러도_같은_형식으로_응답한다() throws Exception {
		mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
					List.of(new ValidationError("from", "to보다 늦을 수 없습니다.")));
		}

		@GetMapping("/test/at")
		String at(@RequestParam OffsetDateTime at) {
			return at.toString();
		}

		@GetMapping("/test/size")
		int size(@RequestParam @Min(1) int size) {
			return size;
		}

		@GetMapping("/test/boom")
		void boom() {
			throw new IllegalStateException("db password leaked");
		}

	}

}

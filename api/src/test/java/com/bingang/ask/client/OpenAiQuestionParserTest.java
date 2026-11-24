package com.bingang.ask.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import com.bingang.ask.service.AskCondition;
import com.bingang.ask.service.AskUnavailableException;
import com.bingang.building.entity.Building;
import com.bingang.common.config.ClockConfig;

@RestClientTest(value = OpenAiQuestionParser.class,
		properties = { "bingang.ask.openai.api-key=test-key", "bingang.ask.openai.model=test-model" })
class OpenAiQuestionParserTest {

	private static final String URL = "https://api.openai.com/v1/chat/completions";

	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 28, 11, 10, 0, 0, ClockConfig.SEOUL);

	private static final List<Building> BUILDINGS = List.of(new Building("E21", "IT관"), new Building("B03", "인문관"));

	@Autowired
	OpenAiQuestionParser parser;

	@Autowired
	MockRestServiceServer server;

	private static String reply(String content) {
		return """
				{"choices":[{"message":{"role":"assistant","content":%s}}]}
				""".formatted("\"" + content.replace("\"", "\\\"") + "\"");
	}

	@Test
	void 질문을_JSON_스키마로_요청하고_조건으로_바꾼다() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
			.andExpect(jsonPath("$.model").value("test-model"))
			.andExpect(jsonPath("$.messages[1].content").value("목요일 1시부터 3시까지 IT관"))
			.andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
			.andRespond(withSuccess(reply(
					"{\"understood\":true,\"date\":\"2026-10-29\",\"start\":\"13:00\",\"end\":\"15:00\",\"buildings\":[\"E21\"]}"),
					MediaType.APPLICATION_JSON));

		AskCondition c = parser.parse("목요일 1시부터 3시까지 IT관", NOW, BUILDINGS);

		assertThat(c).isEqualTo(new AskCondition(true, LocalDate.of(2026, 10, 29), LocalTime.of(13, 0),
				LocalTime.of(15, 0), List.of("E21")));
		server.verify();
	}

	@Test
	void 빈_강의실_질문이_아니면_해석하지_못한_것으로_돌려준다() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess(
					reply("{\"understood\":false,\"date\":null,\"start\":null,\"end\":null,\"buildings\":[]}"),
					MediaType.APPLICATION_JSON));

		assertThat(parser.parse("학식 메뉴 알려줘", NOW, BUILDINGS).understood()).isFalse();
	}

	@Test
	void 시각_값이_잘못되면_해석하지_못한_것으로_본다() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess(
					reply("{\"understood\":true,\"date\":null,\"start\":\"25:00\",\"end\":null,\"buildings\":[]}"),
					MediaType.APPLICATION_JSON));

		assertThat(parser.parse("25시", NOW, BUILDINGS).understood()).isFalse();
	}

	@Test
	void OpenAI_오류는_AskUnavailableException으로_알린다() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> parser.parse("지금 빈 곳", NOW, BUILDINGS)).isInstanceOf(AskUnavailableException.class);
	}

	@Test
	void 지침에_오늘_날짜와_건물_코드_줄임말이_들어간다() {
		String instructions = OpenAiQuestionParser.instructions(NOW, BUILDINGS);

		assertThat(instructions).contains("2026-10-28(수) 11:10", "E21 IT관 (아이티, IT)", "B03 인문관");
	}

	@Test
	void 날짜표는_이번_주부터_3주를_월요일_시작으로_만든다() {
		String table = OpenAiQuestionParser.calendar(LocalDate.of(2026, 10, 28));

		assertThat(table.lines()).hasSize(3);
		assertThat(table.lines().toList().get(0)).startsWith("이번 주: 2026-10-26(월)").contains("2026-10-28(수)=오늘",
				"2026-10-29(목)=내일", "2026-10-30(금)=모레");
		assertThat(table.lines().toList().get(1)).startsWith("다음 주: 2026-11-02(월)").endsWith("2026-11-08(일)");
		assertThat(table.lines().toList().get(2)).startsWith("다다음 주: 2026-11-09(월)");
	}

}

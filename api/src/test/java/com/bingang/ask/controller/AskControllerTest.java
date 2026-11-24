package com.bingang.ask.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.TestcontainersConfiguration;
import com.bingang.ask.service.AskCondition;
import com.bingang.ask.service.AskUnavailableException;
import com.bingang.ask.service.QuestionParser;
import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.common.config.ClockConfig;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

@SpringBootTest(properties = { "bingang.calendar.enabled=false", "bingang.timetable.enabled=false",
		"bingang.ask.daily-limit=2" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AskControllerTest.Config.class })
@Transactional
class AskControllerTest {

	// 2026-10-28(수) 11:10
	static final Instant NOW = Instant.parse("2026-10-28T02:10:00Z");

	static final LocalDate THU = LocalDate.of(2026, 10, 29);

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ClockConfig.SEOUL);
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	BuildingRepository buildings;

	@Autowired
	RoomRepository rooms;

	@Autowired
	RoomScheduleRepository schedules;

	@MockitoBean
	QuestionParser parser;

	@BeforeEach
	void setUp() {
		Building it = buildings.save(new Building("E21", "IT관"));
		Building humanities = buildings.save(new Building("B03", "인문관"));

		Room r101 = rooms.save(new Room(it, "101", (short) 1));
		r101.updateDetails(40, "강의실", "일반");
		Room r102 = rooms.save(new Room(it, "102", (short) 1));
		Room r103 = rooms.save(new Room(it, "103", (short) 1));
		rooms.save(new Room(humanities, "201", (short) 2));

		// 목 13:00~15:00 기준: 101은 15:00부터 수업(구간 내내 빔), 102는 14:00 수업(안 됨), 103은 수업 없음
		schedules.save(new RoomSchedule(r101, DayOfWeek.THURSDAY, LocalTime.of(15, 0), LocalTime.of(16, 15)));
		schedules.save(new RoomSchedule(r102, DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(14, 50)));
		schedules.save(new RoomSchedule(r103, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)));

		given(parser.available()).willReturn(true);
	}

	// 한도 카운터는 컨텍스트를 공유하는 테스트 사이에 이어지므로 테스트마다 다른 IP(X-Forwarded-For)를 쓴다
	private static String body(String question) {
		return "{\"question\":\"" + question + "\"}";
	}

	@Test
	void 구간_내내_비는_강의실만_건물별로_돌려준다() throws Exception {
		given(parser.parse(anyString(), any(), any())).willReturn(
				new AskCondition(true, THU, LocalTime.of(13, 0), LocalTime.of(15, 0), List.of("E21")));

		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.1").contentType(MediaType.APPLICATION_JSON)
				.content(body("목요일 1시부터 3시까지 IT관")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood").value(true))
			.andExpect(jsonPath("$.condition.date").value("2026-10-29"))
			.andExpect(jsonPath("$.condition.dayOfWeek").value(4))
			.andExpect(jsonPath("$.condition.start").value("13:00"))
			.andExpect(jsonPath("$.condition.end").value("15:00"))
			.andExpect(jsonPath("$.buildings.length()").value(1))
			.andExpect(jsonPath("$.buildings[0].code").value("E21"))
			.andExpect(jsonPath("$.buildings[0].rooms.length()").value(2))
			.andExpect(jsonPath("$.buildings[0].rooms[0].number").value("101"))
			.andExpect(jsonPath("$.buildings[0].rooms[0].capacity").value(40))
			.andExpect(jsonPath("$.buildings[0].rooms[0].freeUntil").value("15:00"))
			.andExpect(jsonPath("$.buildings[0].rooms[1].number").value("103"))
			.andExpect(jsonPath("$.buildings[0].rooms[1].freeUntil").doesNotExist());
	}

	@Test
	void 시각을_말하지_않으면_1시간_이상_빈_시간대를_긴_순서로_돌려준다() throws Exception {
		given(parser.parse(anyString(), any(), any()))
			.willReturn(new AskCondition(true, THU, null, null, List.of("E21")));

		// 목: 101은 15:00~16:15 수업, 102는 14:00~14:50 수업, 103은 수업 없음
		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.4").contentType(MediaType.APPLICATION_JSON)
				.content(body("목요일 IT관")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.condition.wholeDay").value(true))
			.andExpect(jsonPath("$.condition.start").value("09:00"))
			.andExpect(jsonPath("$.condition.end").value("21:00"))
			.andExpect(jsonPath("$.buildings[0].rooms.length()").value(3))
			.andExpect(jsonPath("$.buildings[0].rooms[0].number").value("103"))
			.andExpect(jsonPath("$.buildings[0].rooms[0].freeSlots[0][0]").value("09:00"))
			.andExpect(jsonPath("$.buildings[0].rooms[0].freeSlots[0][1]").value("21:00"))
			.andExpect(jsonPath("$.buildings[0].rooms[1].number").value("102"))
			.andExpect(jsonPath("$.buildings[0].rooms[1].freeSlots.length()").value(2))
			.andExpect(jsonPath("$.buildings[0].rooms[2].number").value("101"))
			.andExpect(jsonPath("$.buildings[0].rooms[2].freeSlots[1][0]").value("16:15"));
	}

	@Test
	void 건물을_말하지_않거나_모르는_코드면_전체_건물에서_찾는다() throws Exception {
		given(parser.parse(anyString(), any(), any())).willReturn(
				new AskCondition(true, THU, LocalTime.of(13, 0), LocalTime.of(15, 0), List.of("Z99")));

		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.2").contentType(MediaType.APPLICATION_JSON)
				.content(body("목요일 1시부터 3시")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.buildings.length()").value(2));
	}

	@Test
	void 빈_강의실_질문이_아니면_understood_false() throws Exception {
		given(parser.parse(anyString(), any(), any())).willReturn(AskCondition.notUnderstood());

		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.3").contentType(MediaType.APPLICATION_JSON)
				.content(body("학식 메뉴")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood").value(false))
			.andExpect(jsonPath("$.reason").value("NOT_ROOM_QUESTION"))
			.andExpect(jsonPath("$.buildings").doesNotExist());
	}

	@Test
	void 하루_한도를_넘으면_429() throws Exception {
		given(parser.parse(anyString(), any(), any())).willReturn(AskCondition.notUnderstood());

		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.9").contentType(MediaType.APPLICATION_JSON)
					.content(body("질문"))).andExpect(status().isOk());
		}
		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.9").contentType(MediaType.APPLICATION_JSON)
				.content(body("질문")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("ASK_RATE_LIMITED"));
		// 다른 사용자는 영향 없음
		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.10").contentType(MediaType.APPLICATION_JSON)
				.content(body("질문"))).andExpect(status().isOk());
	}

	@Test
	void AI_호출이_실패하면_503() throws Exception {
		given(parser.parse(anyString(), any(), any())).willThrow(new AskUnavailableException("timeout", null));

		mvc.perform(post("/api/ask").header("X-Forwarded-For", "10.0.0.20").contentType(MediaType.APPLICATION_JSON)
				.content(body("지금 빈 곳")))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ASK_UNAVAILABLE"));
	}

	@Test
	void 키가_없으면_503() throws Exception {
		given(parser.available()).willReturn(false);

		mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(body("지금 빈 곳")))
			.andExpect(status().isServiceUnavailable());
	}

	@Test
	void 빈_질문이나_100자를_넘는_질문은_400() throws Exception {
		mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(body(" ")))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(body("가".repeat(101))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 깨진_JSON은_400_JSON이_아닌_요청은_415() throws Exception {
		mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
		mvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("question=x"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

}

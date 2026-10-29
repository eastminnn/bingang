package com.bingang.calendar.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.TestcontainersConfiguration;
import com.bingang.calendar.entity.AcademicEvent;
import com.bingang.calendar.repository.AcademicEventRepository;

@SpringBootTest(properties = "bingang.calendar.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AcademicEventControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	AcademicEventRepository events;

	@BeforeEach
	void setUp() {
		save(1L, "2026-09-01", "2026-09-01", "개강", null);
		save(2L, "2026-10-20", "2026-10-26", "중간시험", null);
		save(3L, "2026-12-08", "2026-12-08", "공휴일수업대체지정일(09.24.(목) 추석연휴일)", DayOfWeek.THURSDAY);
		save(4L, "2026-12-21", "2027-02-28", "방학", null);
	}

	private void save(long id, String start, String end, String title, DayOfWeek substitute) {
		events.save(new AcademicEvent(id, LocalDate.parse(start), LocalDate.parse(end), title, substitute,
				Instant.now()));
	}

	@Test
	void 기간과_겹치는_일정을_시작일_순으로_돌려준다() throws Exception {
		mvc.perform(get("/api/academic-events?from=2026-10-25&to=2026-12-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2026-10-25"))
			.andExpect(jsonPath("$.to").value("2026-12-31"))
			// 10-20~10-26 중간시험은 기간에 걸쳐 있어서 포함, 9-1 개강은 제외
			.andExpect(jsonPath("$.events.length()").value(3))
			.andExpect(jsonPath("$.events[0].title").value("중간시험"))
			.andExpect(jsonPath("$.events[0].startDate").value("2026-10-20"))
			.andExpect(jsonPath("$.events[0].endDate").value("2026-10-26"))
			.andExpect(jsonPath("$.events[0].substituteDay").doesNotExist())
			.andExpect(jsonPath("$.events[1].substituteDay").value(4))
			.andExpect(jsonPath("$.events[2].title").value("방학"));
	}

	@Test
	void 기간을_안_주면_오늘_앞뒤_6개월이다() throws Exception {
		mvc.perform(get("/api/academic-events"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").exists())
			.andExpect(jsonPath("$.to").exists());
	}

	@Test
	void 시작일이_종료일보다_늦으면_400() throws Exception {
		mvc.perform(get("/api/academic-events?from=2026-12-31&to=2026-01-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
	}

	@Test
	void 기간이_1년을_넘으면_400() throws Exception {
		mvc.perform(get("/api/academic-events?from=2026-01-01&to=2027-06-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
	}

	@Test
	void 날짜_형식이_틀리면_400() throws Exception {
		mvc.perform(get("/api/academic-events?from=2026/10/01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
			.andExpect(jsonPath("$.errors[0].field").value("from"));
	}

}

package com.bingang.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.bingang.TestcontainersConfiguration;
import com.bingang.calendar.entity.AcademicEvent;
import com.bingang.calendar.repository.AcademicEventRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, AcademicCalendarService.class })
class AcademicCalendarServiceTest {

	@Autowired
	AcademicCalendarService calendar;

	@Autowired
	AcademicEventRepository events;

	@BeforeEach
	void setUp() {
		LocalDate dec8 = LocalDate.of(2026, 12, 8);
		events.save(new AcademicEvent(1L, dec8, dec8, "공휴일수업대체지정일(09.24.(목) 추석연휴일)", DayOfWeek.THURSDAY,
				Instant.now()));
		events.save(new AcademicEvent(2L, LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 18), "기말시험", null,
				Instant.now()));
	}

	@Test
	void 보강일에는_지정된_요일을_쓴다() {
		// 2026-12-08은 화요일
		assertThat(calendar.effectiveDayOf(LocalDate.of(2026, 12, 8))).isEqualTo(DayOfWeek.THURSDAY);
	}

	@Test
	void 보강일이_아니면_그날_요일을_쓴다() {
		assertThat(calendar.effectiveDayOf(LocalDate.of(2026, 12, 9))).isEqualTo(DayOfWeek.WEDNESDAY);
		assertThat(calendar.effectiveDayOf(LocalDate.of(2026, 12, 15))).isEqualTo(DayOfWeek.TUESDAY);
	}

}

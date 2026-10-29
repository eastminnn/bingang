package com.bingang.calendar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.bingang.TestcontainersConfiguration;
import com.bingang.calendar.entity.AcademicEvent;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AcademicEventRepositoryTest {

	@Autowired
	AcademicEventRepository events;

	@Autowired
	EntityManager em;

	@Test
	void 보강일의_대체_요일을_저장하고_조회한다() {
		LocalDate day = LocalDate.of(2026, 9, 24);
		events.save(new AcademicEvent(1001L, day, day, "공휴일수업대체지정일(09.24.(목) 추석연휴일)",
				DayOfWeek.THURSDAY, Instant.now()));
		em.flush();
		em.clear();

		AcademicEvent found = events.findBySourceId(1001L).orElseThrow();
		assertThat(found.getSubstituteDay()).isEqualTo(DayOfWeek.THURSDAY);
		assertThat(found.getStartDate()).isEqualTo(day);
	}

	@Test
	void 다시_수집하면_같은_행을_덮어쓴다() {
		LocalDate day = LocalDate.of(2026, 10, 20);
		events.save(new AcademicEvent(2002L, day, day, "중간고사", null, Instant.now()));
		em.flush();

		AcademicEvent event = events.findBySourceId(2002L).orElseThrow();
		event.update(day, day.plusDays(4), "중간고사 기간", null, Instant.now());
		em.flush();
		em.clear();

		assertThat(events.count()).isEqualTo(1);
		assertThat(events.findBySourceId(2002L).orElseThrow().getEndDate()).isEqualTo(day.plusDays(4));
	}

}

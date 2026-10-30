package com.bingang.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** DB가 멈춰도 지표 수집(/actuator/prometheus)이 함께 실패하지 않아야 한다 (#37) */
class CollectorMonitorWithoutDbTest {

	private final CollectorStatusRepository statuses = mock(CollectorStatusRepository.class);

	private final MeterRegistry registry = new SimpleMeterRegistry();

	private final CollectorMonitor monitor = new CollectorMonitor(registry, statuses, Clock.systemUTC());

	@Test
	void 지표를_읽을_때_DB를_조회하지_않는다() {
		registry.get("bingang.collector.last.success").tag("collector", "calendar").gauge().value();
		registry.get("bingang.collector.last.items").tag("collector", "timetable").gauge().value();
		registry.get("bingang.collector.last.duration").tag("collector", "calendar").gauge().value();

		verifyNoInteractions(statuses);
	}

	@Test
	void 시작할_때_DB를_못_읽어도_앱은_뜨고_지표는_0이다() {
		given(statuses.findAll()).willThrow(new DataAccessResourceFailureException("db down"));

		monitor.loadFromDb();

		assertThat(registry.get("bingang.collector.last.success").tag("collector", "calendar").gauge().value())
			.isZero();
	}

	@Test
	void 수집_실패를_기록하다_DB가_실패해도_원래_예외를_던진다() {
		given(statuses.findById(any())).willThrow(new DataAccessResourceFailureException("db down"));

		assertThatThrownBy(() -> monitor.run(CollectorMonitor.CALENDAR, () -> {
			throw new IllegalStateException("calendar page changed");
		}, r -> 0)).isInstanceOf(IllegalStateException.class).hasMessage("calendar page changed");
	}

}

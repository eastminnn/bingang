package com.bingang.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.bingang.TestcontainersConfiguration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, CollectorMonitor.class, CollectorMonitorTest.Config.class })
class CollectorMonitorTest {

	static final Instant NOW = Instant.parse("2026-10-30T05:00:00Z");

	@TestConfiguration
	static class Config {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

	@Autowired
	CollectorMonitor monitor;

	@Autowired
	MeterRegistry registry;

	@Autowired
	CollectorStatusRepository statuses;

	@Test
	void 성공하면_시간과_건수를_남긴다() {
		String result = monitor.run(CollectorMonitor.CALENDAR, () -> "ok", r -> 67);

		assertThat(result).isEqualTo("ok");
		assertThat(registry.get("bingang.collector.duration").tags("collector", "calendar", "outcome", "success")
			.timer()
			.count()).isEqualTo(1);
		assertThat(registry.get("bingang.collector.last.success").tag("collector", "calendar").gauge().value())
			.isEqualTo(NOW.getEpochSecond());
		assertThat(registry.get("bingang.collector.last.items").tag("collector", "calendar").gauge().value())
			.isEqualTo(67);
	}

	@Test
	void 실패하면_실패로_남기고_예외를_그대로_던진다() {
		assertThatThrownBy(() -> monitor.run(CollectorMonitor.TIMETABLE, () -> {
			throw new IllegalStateException("login failed");
		}, r -> 0)).isInstanceOf(IllegalStateException.class);

		assertThat(registry.get("bingang.collector.duration").tags("collector", "timetable", "outcome", "failure")
			.timer()
			.count()).isEqualTo(1);
		CollectorStatus status = statuses.findById(CollectorMonitor.TIMETABLE).orElseThrow();
		assertThat(status.getLastFailureAt()).isEqualTo(NOW);
		assertThat(status.getLastSuccessAt()).isNull();
	}

	@Test
	void 성공한_적이_없으면_마지막_성공_시각은_0이다() {
		assertThat(registry.get("bingang.collector.last.success").tag("collector", "timetable").gauge().value())
			.isZero();
	}

}

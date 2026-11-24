package com.bingang.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class UsageMetricsTest {

	@Test
	void 물어보기_카운터는_요청_전에도_결과마다_0으로_있다() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		new UsageMetrics(registry);

		for (String result : UsageMetrics.ASK_RESULTS) {
			assertThat(registry.get("bingang.ask.requests").tag("result", result).counter().count()).isZero();
		}
	}

	@Test
	void 요청하면_해당_결과만_1_늘어난다() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		UsageMetrics metrics = new UsageMetrics(registry);

		metrics.asked("ok");

		assertThat(registry.get("bingang.ask.requests").tag("result", "ok").counter().count()).isEqualTo(1);
		assertThat(registry.get("bingang.ask.requests").tag("result", "failed").counter().count()).isZero();
	}

}

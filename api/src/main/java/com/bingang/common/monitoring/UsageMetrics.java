package com.bingang.common.monitoring;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 사용 현황 지표.
 * {@code bingang_building_views_total{building, name, mode}}: 건물 강의실 조회 수. mode는 now(지금) / scheduled(시간을 바꿔 봄)
 *
 * 건물 코드는 목록에 보이는 건물(약 30개)로 한정되고 이름은 코드와 1:1이라 라벨로 써도 시계열이 늘지 않는다.
 * 부하 테스트 요청(User-Agent bingang-loadtest)은 세지 않는다.
 */
@Component
public class UsageMetrics {

	static final String LOADTEST_AGENT = "bingang-loadtest";

	private final MeterRegistry registry;

	public UsageMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void buildingViewed(String buildingCode, String buildingName, boolean scheduled, String userAgent) {
		if (userAgent != null && userAgent.contains(LOADTEST_AGENT)) {
			return;
		}
		Counter.builder("bingang.building.views")
			.tag("building", buildingCode)
			.tag("name", buildingName)
			.tag("mode", scheduled ? "scheduled" : "now")
			.description("건물 강의실 조회 수")
			.register(registry)
			.increment();
	}

}

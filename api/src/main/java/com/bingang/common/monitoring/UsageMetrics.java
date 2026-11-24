package com.bingang.common.monitoring;

import java.util.List;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 사용 현황 지표.
 * {@code bingang_building_views_total{building, name, mode}}: 건물 강의실 조회 수. mode는 now(지금) / scheduled(시간을 바꿔 봄)
 *
 * 건물 코드는 목록에 보이는 건물(약 30개)로 한정되고 이름은 코드와 1:1이라 라벨로 써도 시계열이 늘지 않는다.
 * 부하 테스트 요청(User-Agent bingang-loadtest)은 세지 않는다.
 * {@code bingang_ask_requests_total{result}}: 물어보기 요청 수. result는 ok / not_understood / limited / failed / unavailable
 */
@Component
public class UsageMetrics {

	static final String LOADTEST_AGENT = "bingang-loadtest";

	static final List<String> ASK_RESULTS = List.of("ok", "not_understood", "limited", "failed", "unavailable");

	private final MeterRegistry registry;

	/**
	 * 물어보기 카운터는 시작할 때 결과마다 0으로 미리 만든다.
	 * 첫 요청 때 1로 처음 생기면 Prometheus increase()가 0→1을 보지 못해 그 1건을 세지 않는다.
	 * 요청이 적고 블루-그린 배포마다 카운터가 새로 생기므로, 미리 만들지 않으면 대시보드에서 대부분 빠진다
	 */
	public UsageMetrics(MeterRegistry registry) {
		this.registry = registry;
		ASK_RESULTS.forEach(result -> askCounter(result));
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

	// 질문 원문은 남기지 않고 결과 종류만 센다
	public void asked(String result) {
		askCounter(result).increment();
	}

	private Counter askCounter(String result) {
		return Counter.builder("bingang.ask.requests")
			.tag("result", result)
			.description("물어보기 요청 수")
			.register(registry);
	}

}

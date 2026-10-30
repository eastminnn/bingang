package com.bingang.common.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 수집기 실행을 감싸 소요 시간·성공 여부를 지표로 남기고, 마지막 결과를 DB에 저장한다.
 *
 * <ul>
 * <li>{@code bingang_collector_duration_seconds{collector, outcome}}: 실행 시간과 횟수</li>
 * <li>{@code bingang_collector_last_success_seconds{collector}}: 마지막 성공 시각(epoch 초), 없으면 0</li>
 * <li>{@code bingang_collector_last_items{collector}}: 마지막 성공 때 수집한 건수</li>
 * <li>{@code bingang_collector_last_duration_seconds{collector}}: 마지막 실행에 걸린 시간</li>
 * </ul>
 *
 * 지표 값은 메모리에 두고 읽는다. 스크레이프마다 DB를 읽으면 DB 장애 때 지표 수집 전체가 함께 실패한다 (#37).
 * DB는 앱이 뜰 때 한 번 읽고, 수집이 끝날 때 쓴다.
 */
@Component
public class CollectorMonitor {

	public static final String CALENDAR = "calendar";

	public static final String TIMETABLE = "timetable";

	private static final Logger log = LoggerFactory.getLogger(CollectorMonitor.class);

	private final MeterRegistry registry;

	private final CollectorStatusRepository statuses;

	private final Clock clock;

	private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

	public CollectorMonitor(MeterRegistry registry, CollectorStatusRepository statuses, Clock clock) {
		this.registry = registry;
		this.statuses = statuses;
		this.clock = clock;
		for (String name : List.of(CALENDAR, TIMETABLE)) {
			Gauge.builder("bingang.collector.last.success", () -> snapshot(name).lastSuccessEpoch())
				.tag("collector", name)
				.baseUnit("seconds")
				.description("마지막으로 수집에 성공한 시각 (epoch 초)")
				.register(registry);
			Gauge.builder("bingang.collector.last.items", () -> snapshot(name).lastItems())
				.tag("collector", name)
				.description("마지막 성공 때 수집한 건수")
				.register(registry);
			// 하루·주 1회 실행이라 rate()로는 거의 비어 보여서 마지막 값을 따로 내보낸다
			Gauge.builder("bingang.collector.last.duration", () -> snapshot(name).lastDurationSeconds())
				.tag("collector", name)
				.baseUnit("seconds")
				.description("마지막 실행에 걸린 시간")
				.register(registry);
		}
	}

	/** 재시작해도 마지막 성공 시각이 0으로 돌아가지 않도록 DB에 남긴 결과를 불러온다 */
	@EventListener(ApplicationReadyEvent.class)
	public void loadFromDb() {
		try {
			statuses.findAll().forEach(status -> snapshots.put(status.getName(), Snapshot.of(status)));
		}
		catch (DataAccessException e) {
			log.warn("수집기 상태를 불러오지 못했습니다. 다음 수집 때 다시 채워집니다", e);
		}
	}

	/** task를 실행하고 결과를 기록한다. 실패하면 기록한 뒤 예외를 그대로 던진다 */
	public <T> T run(String name, Supplier<T> task, ToIntFunction<T> items) {
		long start = System.nanoTime();
		try {
			T result = task.get();
			Duration duration = Duration.ofNanos(System.nanoTime() - start);
			timer(name, "success").record(duration);
			save(name, status -> status.succeeded(Instant.now(clock), items.applyAsInt(result), duration));
			return result;
		}
		catch (RuntimeException e) {
			Duration duration = Duration.ofNanos(System.nanoTime() - start);
			timer(name, "failure").record(duration);
			save(name, status -> status.failed(Instant.now(clock), duration));
			throw e;
		}
	}

	private Timer timer(String name, String outcome) {
		return Timer.builder("bingang.collector.duration")
			.tag("collector", name)
			.tag("outcome", outcome)
			.description("수집기 실행 시간")
			.register(registry);
	}

	// 기록이 실패해도 수집 결과(성공 값 또는 원래 예외)를 가리지 않는다
	private void save(String name, Consumer<CollectorStatus> change) {
		try {
			CollectorStatus status = statuses.findById(name).orElseGet(() -> new CollectorStatus(name));
			change.accept(status);
			snapshots.put(name, Snapshot.of(statuses.save(status)));
		}
		catch (DataAccessException e) {
			log.warn("수집기 상태를 저장하지 못했습니다: {}", name, e);
		}
	}

	private Snapshot snapshot(String name) {
		return snapshots.getOrDefault(name, Snapshot.EMPTY);
	}

	private record Snapshot(double lastSuccessEpoch, double lastItems, double lastDurationSeconds) {

		static final Snapshot EMPTY = new Snapshot(0, 0, 0);

		static Snapshot of(CollectorStatus status) {
			return new Snapshot(status.getLastSuccessAt() == null ? 0 : status.getLastSuccessAt().getEpochSecond(),
					status.getLastItems() == null ? 0 : status.getLastItems(),
					status.getLastDurationMs() == null ? 0 : status.getLastDurationMs() / 1000.0);
		}

	}

}

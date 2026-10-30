package com.bingang.common.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

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
 * </ul>
 */
@Component
public class CollectorMonitor {

	public static final String CALENDAR = "calendar";

	public static final String TIMETABLE = "timetable";

	private final MeterRegistry registry;

	private final CollectorStatusRepository statuses;

	private final Clock clock;

	public CollectorMonitor(MeterRegistry registry, CollectorStatusRepository statuses, Clock clock) {
		this.registry = registry;
		this.statuses = statuses;
		this.clock = clock;
		// 스크레이프할 때마다 DB 값을 읽어, 앱을 다시 띄워도 마지막 성공 시각이 0으로 돌아가지 않게 한다
		for (String name : List.of(CALENDAR, TIMETABLE)) {
			Gauge.builder("bingang.collector.last.success", () -> lastSuccessEpoch(name))
				.tag("collector", name)
				.baseUnit("seconds")
				.description("마지막으로 수집에 성공한 시각 (epoch 초)")
				.register(registry);
			Gauge.builder("bingang.collector.last.items", () -> lastItems(name))
				.tag("collector", name)
				.description("마지막 성공 때 수집한 건수")
				.register(registry);
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

	private void save(String name, Consumer<CollectorStatus> change) {
		CollectorStatus status = statuses.findById(name).orElseGet(() -> new CollectorStatus(name));
		change.accept(status);
		statuses.save(status);
	}

	private double lastSuccessEpoch(String name) {
		return statuses.findById(name)
			.map(CollectorStatus::getLastSuccessAt)
			.map(Instant::getEpochSecond)
			.orElse(0L);
	}

	private double lastItems(String name) {
		return statuses.findById(name).map(CollectorStatus::getLastItems).map(Integer::doubleValue).orElse(0.0);
	}

}

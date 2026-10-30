package com.bingang.common.monitoring;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 수집기(calendar, timetable)의 마지막 실행 결과 */
@Entity
public class CollectorStatus {

	@Id
	private String name;

	private Instant lastSuccessAt;

	private Instant lastFailureAt;

	private Integer lastItems;

	private Long lastDurationMs;

	protected CollectorStatus() {
	}

	public CollectorStatus(String name) {
		this.name = name;
	}

	public void succeeded(Instant at, int items, Duration duration) {
		this.lastSuccessAt = at;
		this.lastItems = items;
		this.lastDurationMs = duration.toMillis();
	}

	public void failed(Instant at, Duration duration) {
		this.lastFailureAt = at;
		this.lastDurationMs = duration.toMillis();
	}

	public String getName() {
		return name;
	}

	public Instant getLastSuccessAt() {
		return lastSuccessAt;
	}

	public Instant getLastFailureAt() {
		return lastFailureAt;
	}

	public Integer getLastItems() {
		return lastItems;
	}

	public Long getLastDurationMs() {
		return lastDurationMs;
	}

}

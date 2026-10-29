package com.bingang.room.service;

import java.time.LocalTime;

/** 강의실이 사용 중인 시간 [start, end) */
public record Session(LocalTime start, LocalTime end) {

	public Session {
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("start는 end보다 빨라야 합니다: " + start + " - " + end);
		}
	}

	boolean contains(LocalTime time) {
		return !time.isBefore(start) && time.isBefore(end);
	}

}

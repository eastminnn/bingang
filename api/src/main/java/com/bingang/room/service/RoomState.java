package com.bingang.room.service;

import java.time.LocalTime;

/**
 * 어느 시각의 강의실 상태.
 * 비어 있으면 freeUntil(다음 수업 시작, 오늘 남은 수업이 없으면 null), 사용 중이면 freeAt(이어진 수업이 끝나는 시각)
 */
public record RoomState(boolean busy, LocalTime freeUntil, LocalTime freeAt) {

	static RoomState free(LocalTime until) {
		return new RoomState(false, until, null);
	}

	static RoomState busyUntil(LocalTime freeAt) {
		return new RoomState(true, null, freeAt);
	}

}

package com.bingang.room.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.bingang.building.entity.Building;
import com.bingang.room.entity.Room;

/**
 * date의 [start, end) 안에서 비어 있는 강의실 (물어보기, #74). dayOfWeek는 판정에 실제로 쓴 요일 (보강일이면 지정된 요일).
 * wholeDay가 false면 구간 내내 비는 강의실만, true면 구간 안에 빈 시간대(slots)가 있는 강의실을 담는다
 */
public record RangeAvailability(LocalDate date, DayOfWeek dayOfWeek, LocalTime start, LocalTime end, boolean wholeDay,
		List<BuildingRooms> buildings) {

	public record BuildingRooms(Building building, List<RoomFree> rooms) {
	}

	/**
	 * freeUntil: 구간 내내 비는 경우 다음 수업 시작 (null = 그날 끝까지).
	 * slots: wholeDay일 때 빈 시간대 (시간 순), 아니면 빈 목록
	 */
	public record RoomFree(Room room, LocalTime freeUntil, List<Session> slots) {

		Duration longestSlot() {
			return slots.stream().map(s -> Duration.between(s.start(), s.end())).max(Duration::compareTo)
				.orElse(Duration.ZERO);
		}

	}

}

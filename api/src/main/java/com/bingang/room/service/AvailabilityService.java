package com.bingang.room.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.building.entity.Building;
import com.bingang.calendar.service.AcademicCalendarService;
import com.bingang.common.config.ClockConfig;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

	// 호수는 숫자 크기 순서 (예: 105 < 201 < 1001), 숫자가 아니면 뒤에 문자열 순서
	private static final Comparator<Room> BY_NUMBER = Comparator
		.comparing((Room room) -> leadingNumber(room.getNumber()))
		.thenComparing(Room::getNumber);

	private final RoomRepository rooms;

	private final RoomScheduleRepository schedules;

	private final AcademicCalendarService calendar;

	public AvailabilityService(RoomRepository rooms, RoomScheduleRepository schedules,
			AcademicCalendarService calendar) {
		this.rooms = rooms;
		this.schedules = schedules;
		this.calendar = calendar;
	}

	public BuildingAvailability ofBuilding(Building building, Instant at) {
		ZonedDateTime seoul = at.atZone(ClockConfig.SEOUL);
		DayOfWeek day = calendar.effectiveDayOf(seoul.toLocalDate());
		Map<Long, DaySchedule> byRoom = daySchedules(schedules.findByBuildingAndDay(building, day));

		List<RoomStatus> statuses = rooms.findByBuilding(building)
			.stream()
			.sorted(BY_NUMBER)
			.map(room -> toStatus(room, byRoom.getOrDefault(room.getId(), DaySchedule.of(List.of())),
					seoul.toLocalTime()))
			.toList();
		return new BuildingAvailability(seoul, day, building, statuses);
	}

	/** 건물 id → 빈 강의실 수와 전체 강의실 수 */
	public Map<Long, RoomCount> roomCounts(Instant at) {
		ZonedDateTime seoul = at.atZone(ClockConfig.SEOUL);
		DayOfWeek day = calendar.effectiveDayOf(seoul.toLocalDate());
		Map<Long, DaySchedule> byRoom = daySchedules(schedules.findByDay(day));

		Map<Long, RoomCount> counts = new HashMap<>();
		for (Room room : rooms.findAll()) {
			DaySchedule schedule = byRoom.get(room.getId());
			boolean free = schedule == null || !schedule.stateAt(seoul.toLocalTime()).busy();
			counts.compute(room.getBuilding().getId(),
					(id, count) -> (count == null ? new RoomCount(0, 0) : count).add(free));
		}
		return counts;
	}

	private RoomStatus toStatus(Room room, DaySchedule schedule, LocalTime time) {
		return new RoomStatus(room, schedule.stateAt(time), schedule.sessions());
	}

	private static Map<Long, DaySchedule> daySchedules(List<RoomSchedule> schedules) {
		return schedules.stream()
			.collect(Collectors.groupingBy(s -> s.getRoom().getId(),
					Collectors.collectingAndThen(
							Collectors.mapping(s -> new Session(s.getStartTime(), s.getEndTime()), Collectors.toList()),
							DaySchedule::of)));
	}

	private static int leadingNumber(String number) {
		int end = 0;
		while (end < number.length() && Character.isDigit(number.charAt(end))) {
			end++;
		}
		return end == 0 ? Integer.MAX_VALUE : Integer.parseInt(number.substring(0, end));
	}

}

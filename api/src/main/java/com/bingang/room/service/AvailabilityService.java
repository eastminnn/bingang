package com.bingang.room.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AvailabilityService {

	// 호수는 숫자 크기 순서 (예: 105 < 201 < 1001), 숫자가 아니면 뒤에 문자열 순서
	private static final Comparator<Room> BY_NUMBER = Comparator
		.comparing((Room room) -> leadingNumber(room.getNumber()))
		.thenComparing(Room::getNumber);

	private static final DaySchedule EMPTY = DaySchedule.of(List.of());

	private final RoomRepository rooms;
	private final RoomScheduleRepository schedules;
	private final AcademicCalendarService calendar;

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

	/**
	 * 그날 [start, end) 동안 계속 비어 있는 강의실을 건물별로 (물어보기, #74).
	 * 건물 순서는 받은 순서, 강의실은 호수 순서. 빈 강의실이 없는 건물도 빈 목록으로 남긴다
	 */
	public RangeAvailability freeBetween(LocalDate date, LocalTime start, LocalTime end, List<Building> buildings) {
		return inRange(date, start, end, false, buildings, (room, schedule) -> schedule.freeBetween(start, end)
				? new RangeAvailability.RoomFree(room, schedule.stateAt(start).freeUntil(), List.of()) : null);
	}

	/**
	 * 그날 [start, end) 안에 minimum 이상 비는 시간대가 있는 강의실을 건물별로 (물어보기에서 시간을 말하지 않았을 때).
	 * 강의실은 가장 긴 빈 시간대가 긴 순서, 같으면 호수 순서
	 */
	public RangeAvailability freeSlots(LocalDate date, LocalTime start, LocalTime end, Duration minimum,
			List<Building> buildings) {
		Comparator<RangeAvailability.RoomFree> longestFirst = Comparator
			.comparing(RangeAvailability.RoomFree::longestSlot).reversed();
		RangeAvailability found = inRange(date, start, end, true, buildings, (room, schedule) -> {
			List<Session> slots = schedule.freeSlots(start, end)
				.stream()
				.filter(s -> Duration.between(s.start(), s.end()).compareTo(minimum) >= 0)
				.toList();
			return slots.isEmpty() ? null : new RangeAvailability.RoomFree(room, null, slots);
		});
		// inRange가 호수 순서로 넘기므로 안정 정렬이면 같은 길이끼리는 호수 순서가 유지된다
		return new RangeAvailability(found.date(), found.dayOfWeek(), start, end, true, found.buildings()
			.stream()
			.map(b -> new RangeAvailability.BuildingRooms(b.building(), b.rooms().stream().sorted(longestFirst).toList()))
			.toList());
	}

	// 건물마다 강의실을 호수 순서로 돌며 judge가 null이 아닌 것만 모은다
	private RangeAvailability inRange(LocalDate date, LocalTime start, LocalTime end, boolean wholeDay,
			List<Building> buildings, BiFunction<Room, DaySchedule, RangeAvailability.RoomFree> judge) {
		DayOfWeek day = calendar.effectiveDayOf(date);
		Map<Long, DaySchedule> byRoom = daySchedules(schedules.findByDay(day));
		Map<Long, List<Room>> roomsByBuilding = rooms.findAllWithBuilding()
			.stream()
			.collect(Collectors.groupingBy(room -> room.getBuilding().getId()));

		List<RangeAvailability.BuildingRooms> result = buildings.stream()
			.map(building -> new RangeAvailability.BuildingRooms(building,
					roomsByBuilding.getOrDefault(building.getId(), List.of())
						.stream()
						.sorted(BY_NUMBER)
						.map(room -> judge.apply(room, byRoom.getOrDefault(room.getId(), EMPTY)))
						.filter(Objects::nonNull)
						.toList()))
			.toList();
		return new RangeAvailability(date, day, start, end, wholeDay, result);
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

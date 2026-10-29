package com.bingang.room.collect;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.room.collect.TimetableAssembler.SlotKey;
import com.bingang.room.collect.TimetableAssembler.Timetable;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

@Service
public class TimetableCollector {

	private static final Logger log = LoggerFactory.getLogger(TimetableCollector.class);

	// 수업이 있는 요일: 월~토 (요청의 sbDate 첫 자리 1~6)
	private static final List<DayOfWeek> DAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

	private static final Pattern BASEMENT = Pattern.compile("^B(\\d)");

	private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d{3,})");

	private final YuPortalClient client;

	private final BuildingRepository buildings;

	private final RoomRepository rooms;

	private final RoomScheduleRepository schedules;

	private final TransactionTemplate transaction;

	private final String year;

	private final String semesterCode;

	private final Duration interval;

	private final Set<String> hiddenCodes;

	public TimetableCollector(YuPortalClient client, BuildingRepository buildings, RoomRepository rooms,
			RoomScheduleRepository schedules, TransactionTemplate transaction,
			@Value("${bingang.timetable.year}") String year,
			@Value("${bingang.timetable.semester-code}") String semesterCode,
			@Value("${bingang.timetable.request-interval}") Duration interval,
			@Value("${bingang.building.hidden-codes:}") List<String> hiddenCodes) {
		this.client = client;
		this.buildings = buildings;
		this.rooms = rooms;
		this.schedules = schedules;
		this.transaction = transaction;
		this.year = year;
		this.semesterCode = semesterCode;
		this.interval = interval;
		this.hiddenCodes = Set.copyOf(hiddenCodes);
	}

	/**
	 * 요일 × 시간 칸마다 빈 강의실을 조회해 시간표 전체를 교체한다.
	 * 조회 중 하나라도 실패하면 예외를 던지고 기존 데이터는 그대로 둔다.
	 */
	public Result collect() {
		YuPortalClient.Session session = client.login();
		Map<SlotKey, List<FreeRoom>> freeBySlot = new LinkedHashMap<>();
		for (DayOfWeek day : DAYS) {
			StringBuilder counts = new StringBuilder();
			for (Slot slot : Slot.ALL) {
				pause();                                // 학교 서버 부담을 줄이려고 1초에 1건
				List<FreeRoom> free = session.freeRooms(year, semesterCode, day.getValue(), slot);
				freeBySlot.put(new SlotKey(day, slot), free);
				counts.append(' ').append(slot.label()).append('=').append(free.size());
			}
			// 칸마다 숫자가 달라야 시간 조건이 반영된 것이다
			log.info("빈 강의실 수 {}:{}", day, counts);
		}
		Timetable timetable = TimetableAssembler.assemble(freeBySlot);
		return transaction.execute(status -> replace(timetable));
	}

	private Result replace(Timetable timetable) {
		schedules.deleteAllSchedules();

		Map<String, Building> buildingByCode = buildings.findAll()
			.stream()
			.collect(Collectors.toMap(Building::getCode, Function.identity()));
		Map<String, Room> leftover = rooms.findAllWithBuilding()
			.stream()
			.collect(Collectors.toMap(r -> r.getBuilding().getCode() + "-" + r.getNumber(), Function.identity()));

		Map<String, Room> roomByKey = new HashMap<>();
		for (FreeRoom collected : timetable.rooms()) {
			Building building = buildingByCode.computeIfAbsent(collected.buildingCode(),
					code -> buildings.save(new Building(code, collected.buildingName())));
			if (!building.getName().equals(collected.buildingName())) {
				building.rename(collected.buildingName());
			}
			Room room = leftover.remove(collected.key());
			if (room == null) {
				room = rooms.save(new Room(building, collected.number(), floorOf(collected.number())));
			}
			room.updateDetails(collected.capacity(), collected.name(), collected.type());
			roomByKey.put(collected.key(), room);
		}
		// 이번 수집에 없는 강의실은 지운다 (계속 비어 있는 것처럼 보이지 않게)
		rooms.deleteAll(leftover.values());

		// 강의실이 남지 않은 건물과 설정으로 숨긴 건물은 목록에서 뺀다
		Set<String> withRooms = roomByKey.values()
			.stream()
			.map(room -> room.getBuilding().getCode())
			.collect(Collectors.toSet());
		buildingByCode.values()
			.forEach(b -> b.updateVisibility(withRooms.contains(b.getCode()) && !hiddenCodes.contains(b.getCode())));

		schedules.saveAll(timetable.sessions()
			.stream()
			.map(s -> new RoomSchedule(roomByKey.get(s.roomKey()), s.day(), s.start(), s.end()))
			.toList());
		return new Result(buildingByCode.size(), roomByKey.size(), timetable.sessions().size(), leftover.size(),
				timetable.unusedRooms());
	}

	/** 호수로 층을 정한다: 201 → 2, 1012 → 10, B101 → -1, 그 외 null */
	static Short floorOf(String number) {
		Matcher basement = BASEMENT.matcher(number);
		if (basement.find()) {
			return (short) -Integer.parseInt(basement.group(1));
		}
		Matcher digits = LEADING_DIGITS.matcher(number);
		if (digits.find()) {
			return (short) (Integer.parseInt(digits.group(1)) / 100);
		}
		return null;
	}

	private void pause() {
		if (interval.isZero()) {
			return;
		}
		try {
			Thread.sleep(interval);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("시간표 수집이 중단되었습니다.", e);
		}
	}

	/** unusedRooms: 일주일 내내 수업이 없어 저장하지 않은 강의실 수 */
	public record Result(int buildings, int rooms, int sessions, int removedRooms, int unusedRooms) {
	}

}

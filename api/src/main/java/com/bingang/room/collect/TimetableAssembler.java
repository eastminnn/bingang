package com.bingang.room.collect;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 칸별 "빈 강의실" 목록을 뒤집어 강의실별 "사용 중 시간"으로 만든다.
 * 전체 강의실 = 한 번이라도 응답에 나온 강의실. 그 칸에 없으면 사용 중이다.
 */
public final class TimetableAssembler {

	/** 강의실이 이보다 적으면 수집이 잘못된 것으로 본다 */
	static final int MIN_ROOMS = 10;

	private TimetableAssembler() {
	}

	public static Timetable assemble(Map<SlotKey, List<FreeRoom>> freeBySlot) {
		Map<String, FreeRoom> rooms = new LinkedHashMap<>();
		freeBySlot.values().forEach(list -> list.forEach(room -> rooms.putIfAbsent(room.key(), room)));
		if (rooms.size() < MIN_ROOMS) {
			throw new IllegalStateException("수집된 강의실이 " + rooms.size() + "개뿐입니다.");
		}

		Map<SlotKey, Set<String>> freeKeys = new LinkedHashMap<>();
		freeBySlot.forEach((slot, list) -> freeKeys.put(slot,
				list.stream().map(FreeRoom::key).collect(Collectors.toCollection(HashSet::new))));
		// 모든 칸의 결과가 같으면 학교 시스템이 시간을 무시한 것이라 시간표로 쓸 수 없다
		if (new HashSet<>(freeKeys.values()).size() <= 1) {
			throw new IllegalStateException("모든 시간 칸의 빈 강의실이 같습니다. 시간 조건이 반영되지 않았을 수 있습니다.");
		}

		List<BusySession> sessions = new ArrayList<>();
		for (String key : rooms.keySet()) {
			for (DayOfWeek day : days(freeKeys)) {
				LocalTime start = null;
				LocalTime end = null;
				for (Slot slot : Slot.ALL) {
					Set<String> free = freeKeys.get(new SlotKey(day, slot));
					boolean busy = free != null && !free.contains(key);
					if (busy && start != null && slot.start().equals(end)) {
						end = slot.end();               // 바로 이어지는 칸은 하나로 합친다
					}
					else {
						if (start != null) {
							sessions.add(new BusySession(key, day, start, end));
							start = null;
						}
						if (busy) {
							start = slot.start();
							end = slot.end();
						}
					}
				}
				if (start != null) {
					sessions.add(new BusySession(key, day, start, end));
				}
			}
		}
		return new Timetable(List.copyOf(rooms.values()), sessions);
	}

	private static List<DayOfWeek> days(Map<SlotKey, Set<String>> freeKeys) {
		return freeKeys.keySet().stream().map(SlotKey::day).distinct().sorted().toList();
	}

	public record SlotKey(DayOfWeek day, Slot slot) {
	}

	public record BusySession(String roomKey, DayOfWeek day, LocalTime start, LocalTime end) {
	}

	public record Timetable(List<FreeRoom> rooms, List<BusySession> sessions) {
	}

}

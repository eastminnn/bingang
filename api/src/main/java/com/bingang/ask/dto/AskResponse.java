package com.bingang.ask.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.bingang.ask.service.AskResult;
import com.bingang.room.service.RangeAvailability;

/**
 * understood=false면 condition·buildings는 null이고 reason에 이유
 * (NOT_ROOM_QUESTION / OUT_OF_RANGE: 지났거나 2주 넘음 / OUT_OF_HOURS: 09:00~21:00 밖 / INVALID_TIME: 시작이 끝보다 늦음).
 * condition.dayOfWeek는 판정에 실제로 쓴 요일 (1=월 ~ 7=일, 보강일이면 지정된 요일).
 * condition.wholeDay=false: 구간 내내 비는 강의실. rooms[].freeUntil은 다음 수업 시작 (null = 그날 끝까지 비어 있음)
 * condition.wholeDay=true: 시간을 말하지 않아 그날 1시간 이상 비는 시간대. rooms[].freeSlots = [["09:00","13:00"], …]
 */
public record AskResponse(boolean understood, String reason, Condition condition, List<BuildingItem> buildings) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static AskResponse from(AskResult result) {
		if (!result.understood()) {
			return new AskResponse(false, result.rejection().name(), null, null);
		}
		RangeAvailability r = result.rooms();
		return new AskResponse(true, null,
				new Condition(r.date(), r.dayOfWeek().getValue(), format(r.start()), format(r.end()), r.wholeDay(),
						r.buildings().stream().map(b -> b.building().getCode()).toList()),
				r.buildings().stream()
					.map(b -> new BuildingItem(b.building().getCode(), b.building().getName(),
							b.rooms().stream().map(AskResponse::toItem).toList()))
					.toList());
	}

	private static RoomItem toItem(RangeAvailability.RoomFree free) {
		return new RoomItem(free.room().getNumber(), free.room().getFloor(), free.room().getCapacity(),
				format(free.freeUntil()), free.slots().stream().map(s -> List.of(format(s.start()), format(s.end()))).toList());
	}

	private static String format(LocalTime time) {
		return time == null ? null : time.format(HH_MM);
	}

	public record Condition(LocalDate date, int dayOfWeek, String start, String end, boolean wholeDay,
			List<String> buildings) {
	}

	public record BuildingItem(String code, String name, List<RoomItem> rooms) {
	}

	public record RoomItem(String number, Short floor, Integer capacity, String freeUntil, List<List<String>> freeSlots) {
	}

}

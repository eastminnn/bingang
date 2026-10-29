package com.bingang.room.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.bingang.room.service.BuildingAvailability;
import com.bingang.room.service.RoomStatus;

/** dayOfWeek는 판정에 실제로 쓴 요일 (1=월 ~ 7=일, 보강일이면 지정된 요일) */
public record BuildingRoomsResponse(OffsetDateTime at, int dayOfWeek, BuildingInfo building, List<RoomItem> rooms) {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	public static BuildingRoomsResponse from(BuildingAvailability availability) {
		return new BuildingRoomsResponse(availability.at().toOffsetDateTime(), availability.dayOfWeek().getValue(),
				new BuildingInfo(availability.building().getCode(), availability.building().getName()),
				availability.rooms().stream().map(BuildingRoomsResponse::toItem).toList());
	}

	private static RoomItem toItem(RoomStatus status) {
		return new RoomItem(status.room().getNumber(), status.room().getFloor(), status.room().getCapacity(),
				status.state().busy(), format(status.state().freeUntil()), format(status.state().freeAt()),
				status.sessions().stream().map(s -> List.of(format(s.start()), format(s.end()))).toList());
	}

	private static String format(LocalTime time) {
		return time == null ? null : time.format(HH_MM);
	}

	public record BuildingInfo(String code, String name) {
	}

	/** sessions: 그날 수업 [["09:00","10:15"], …], 타임라인 막대용 */
	public record RoomItem(String number, Short floor, Integer capacity, boolean busy, String freeUntil,
			String freeAt, List<List<String>> sessions) {
	}

}

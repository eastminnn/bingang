package com.bingang.room.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.bingang.building.entity.Building;
import com.bingang.common.config.ClockConfig;
import com.bingang.room.service.RoomCount;

public record BuildingListResponse(OffsetDateTime at, List<BuildingSummary> buildings) {

	private static final RoomCount NO_ROOMS = new RoomCount(0, 0);

	public record BuildingSummary(String code, String name, String zone, long freeRooms, long totalRooms) {
	}

	public static BuildingListResponse from(Instant at, List<Building> buildings, Map<Long, RoomCount> counts) {
		List<BuildingSummary> summaries = buildings.stream().map(building -> {
			RoomCount count = counts.getOrDefault(building.getId(), NO_ROOMS);
			return new BuildingSummary(building.getCode(), building.getName(), building.getZone(), count.free(),
					count.total());
		}).toList();
		return new BuildingListResponse(at.atZone(ClockConfig.SEOUL).toOffsetDateTime(), summaries);
	}
}

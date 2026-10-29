package com.bingang.room.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BuildingListResponse(OffsetDateTime at, List<BuildingSummary> buildings) {

	public record BuildingSummary(String code, String name, String zone, long freeRooms, long totalRooms) {
	}

}

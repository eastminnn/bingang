package com.bingang.room.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bingang.building.entity.Building;
import com.bingang.building.service.BuildingService;
import com.bingang.common.config.ClockConfig;
import com.bingang.room.dto.BuildingListResponse;
import com.bingang.room.dto.BuildingListResponse.BuildingSummary;
import com.bingang.room.dto.BuildingRoomsResponse;
import com.bingang.room.service.AvailabilityService;
import com.bingang.room.service.RoomCount;

@RestController
@RequestMapping("/api/buildings")
public class RoomController {

	private static final RoomCount NO_ROOMS = new RoomCount(0, 0);

	private final BuildingService buildingService;

	private final AvailabilityService availabilityService;

	private final Clock clock;

	public RoomController(BuildingService buildingService, AvailabilityService availabilityService, Clock clock) {
		this.buildingService = buildingService;
		this.availabilityService = availabilityService;
		this.clock = clock;
	}

	@GetMapping
	public BuildingListResponse buildings(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at) {
		Instant instant = resolve(at);
		Map<Long, RoomCount> counts = availabilityService.roomCounts(instant);
		List<BuildingSummary> summaries = buildingService.findVisible().stream().map(building -> {
			RoomCount count = counts.getOrDefault(building.getId(), NO_ROOMS);
			return new BuildingSummary(building.getCode(), building.getName(), building.getZone(), count.free(),
					count.total());
		}).toList();
		return new BuildingListResponse(instant.atZone(ClockConfig.SEOUL).toOffsetDateTime(), summaries);
	}

	@GetMapping("/{code}/rooms")
	public BuildingRoomsResponse rooms(@PathVariable String code,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at) {
		Building building = buildingService.getVisible(code);
		return BuildingRoomsResponse.from(availabilityService.ofBuilding(building, resolve(at)));
	}

	// at이 없으면 지금. 응답에 나노초까지 찍히지 않도록 초 단위로 자른다
	private Instant resolve(OffsetDateTime at) {
		Instant instant = at == null ? Instant.now(clock) : at.toInstant();
		return instant.truncatedTo(ChronoUnit.SECONDS);
	}

}

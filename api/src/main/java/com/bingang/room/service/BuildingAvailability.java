package com.bingang.room.service;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.List;

import com.bingang.building.entity.Building;

/** dayOfWeek는 판정에 실제로 쓴 요일 (보강일이면 지정된 요일) */
public record BuildingAvailability(ZonedDateTime at, DayOfWeek dayOfWeek, Building building, List<RoomStatus> rooms) {
}

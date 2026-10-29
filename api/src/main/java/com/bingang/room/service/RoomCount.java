package com.bingang.room.service;

public record RoomCount(long free, long total) {

	RoomCount add(boolean isFree) {
		return new RoomCount(free + (isFree ? 1 : 0), total + 1);
	}

}

package com.bingang.room.service;

import java.util.List;

import com.bingang.room.entity.Room;

public record RoomStatus(Room room, RoomState state, List<Session> sessions) {
}

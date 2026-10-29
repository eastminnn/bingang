package com.bingang.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bingang.room.entity.Building;
import com.bingang.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

	List<Room> findByBuilding(Building building);

}

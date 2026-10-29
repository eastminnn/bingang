package com.bingang.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "building_id")
	private Building building;

	/** 호수 (예: 201) */
	@Column(nullable = false, length = 20)
	private String number;

	/** 층, 알 수 없으면 null */
	private Short floor;

	private Integer capacity;

	@Column(length = 100)
	private String name;

	@Column(length = 50)
	private String type;

	protected Room() {
	}

	public Room(Building building, String number, Short floor) {
		this.building = building;
		this.number = number;
		this.floor = floor;
	}

	public void updateDetails(Integer capacity, String name, String type) {
		this.capacity = capacity;
		this.name = name;
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public Building getBuilding() {
		return building;
	}

	public String getNumber() {
		return number;
	}

	public Short getFloor() {
		return floor;
	}

	public Integer getCapacity() {
		return capacity;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

}

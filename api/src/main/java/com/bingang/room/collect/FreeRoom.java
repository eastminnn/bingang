package com.bingang.room.collect;

/** 빈강의실조회 응답의 강의실 한 줄. key는 건물 코드-호수 (예: B03-201) */
public record FreeRoom(String buildingCode, String buildingName, String number, String name, String type,
		Integer capacity) {

	public String key() {
		return buildingCode + "-" + number;
	}

}

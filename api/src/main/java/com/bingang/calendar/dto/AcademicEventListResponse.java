package com.bingang.calendar.dto;

import java.time.LocalDate;
import java.util.List;

import com.bingang.calendar.entity.AcademicEvent;

public record AcademicEventListResponse(LocalDate from, LocalDate to, List<AcademicEventResponse> events) {

	public static AcademicEventListResponse from(LocalDate from, LocalDate to, List<AcademicEvent> events) {
		return new AcademicEventListResponse(from, to, events.stream().map(AcademicEventResponse::from).toList());
	}
}

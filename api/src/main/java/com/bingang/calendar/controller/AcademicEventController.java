package com.bingang.calendar.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bingang.calendar.dto.AcademicEventListResponse;
import com.bingang.calendar.dto.AcademicEventResponse;
import com.bingang.calendar.service.AcademicCalendarService;

@RestController
@RequestMapping("/api/academic-events")
public class AcademicEventController {

	private final AcademicCalendarService calendarService;

	private final Clock clock;

	public AcademicEventController(AcademicCalendarService calendarService, Clock clock) {
		this.calendarService = calendarService;
		this.clock = clock;
	}

	// 기간을 주지 않으면 오늘 기준 앞뒤 6개월 (한 학기와 앞뒤 방학이 들어간다)
	@GetMapping
	public AcademicEventListResponse events(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from != null ? from : today.minusMonths(6);
		LocalDate end = to != null ? to : today.plusMonths(6);
		return new AcademicEventListResponse(start, end,
				calendarService.findBetween(start, end).stream().map(AcademicEventResponse::from).toList());
	}

}

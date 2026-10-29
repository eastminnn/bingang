package com.bingang.calendar.dto;

import java.time.LocalDate;

import com.bingang.calendar.entity.AcademicEvent;

/** title은 학교 원문 그대로, substituteDay는 보강일이면 지정된 요일(1=월 ~ 7=일) */
public record AcademicEventResponse(LocalDate startDate, LocalDate endDate, String title, Integer substituteDay) {

	public static AcademicEventResponse from(AcademicEvent event) {
		return new AcademicEventResponse(event.getStartDate(), event.getEndDate(), event.getTitle(),
				event.getSubstituteDay() == null ? null : event.getSubstituteDay().getValue());
	}

}

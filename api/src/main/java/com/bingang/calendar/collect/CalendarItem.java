package com.bingang.calendar.collect;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** 학교 페이지에서 읽은 학사 일정 한 건 */
public record CalendarItem(Long sourceId, LocalDate startDate, LocalDate endDate, String title,
		DayOfWeek substituteDay) {
}

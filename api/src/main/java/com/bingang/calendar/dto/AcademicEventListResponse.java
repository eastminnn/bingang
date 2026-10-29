package com.bingang.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record AcademicEventListResponse(LocalDate from, LocalDate to, List<AcademicEventResponse> events) {
}

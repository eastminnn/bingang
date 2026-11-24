package com.bingang.ask.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 질문을 해석한 조건. understood가 false면 rejection에 이유가 있고 나머지 값은 무시한다.
 * buildingCodes가 비어 있으면 모든 건물에서 찾는다.
 * wholeDay: 시간을 말하지 않아 그날 [start, end) 안의 빈 시간대를 보여 주는 경우 (정규화 뒤에만 true)
 */
public record AskCondition(boolean understood, LocalDate date, LocalTime start, LocalTime end,
		List<String> buildingCodes, boolean wholeDay, Rejection rejection) {

	/** 답하지 못한 이유. 화면이 이유별로 안내 문구를 고른다 */
	public enum Rejection {

		/** 빈 강의실 질문이 아니거나 해석하지 못함 */
		NOT_ROOM_QUESTION,
		/** 지난 날짜이거나 2주보다 먼 날짜 */
		OUT_OF_RANGE,
		/** 운영 시간(09:00~21:00) 밖 */
		OUT_OF_HOURS,
		/** 시작이 끝보다 늦음 */
		INVALID_TIME

	}

	/** AI가 돌려준 조건 (wholeDay는 서버가 정규화하며 정한다) */
	public AskCondition(boolean understood, LocalDate date, LocalTime start, LocalTime end, List<String> buildingCodes) {
		this(understood, date, start, end, buildingCodes, false, understood ? null : Rejection.NOT_ROOM_QUESTION);
	}

	public AskCondition(boolean understood, LocalDate date, LocalTime start, LocalTime end, List<String> buildingCodes,
			boolean wholeDay) {
		this(understood, date, start, end, buildingCodes, wholeDay, understood ? null : Rejection.NOT_ROOM_QUESTION);
	}

	public static AskCondition notUnderstood() {
		return rejected(Rejection.NOT_ROOM_QUESTION);
	}

	public static AskCondition rejected(Rejection rejection) {
		return new AskCondition(false, null, null, null, List.of(), false, rejection);
	}

}

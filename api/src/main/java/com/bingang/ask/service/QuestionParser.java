package com.bingang.ask.service;

import java.time.ZonedDateTime;
import java.util.List;

import com.bingang.building.entity.Building;

/** 자연어 질문을 조건으로 바꾼다. 계산은 하지 않는다 (빈 강의실 판정은 기존 로직이 한다) */
public interface QuestionParser {

	/** 사용할 수 없으면(API 키 없음 등) false. 이때 물어보기는 503으로 응답한다 */
	boolean available();

	/** @throws AskUnavailableException 외부 호출이 실패했을 때 */
	AskCondition parse(String question, ZonedDateTime now, List<Building> buildings);

}

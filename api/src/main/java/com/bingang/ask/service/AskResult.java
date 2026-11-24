package com.bingang.ask.service;

import com.bingang.ask.service.AskCondition.Rejection;
import com.bingang.room.service.RangeAvailability;

/** understood=false면 rooms는 null이고 rejection에 이유가 있다 */
public record AskResult(boolean understood, Rejection rejection, RangeAvailability rooms) {

	public AskResult(boolean understood, RangeAvailability rooms) {
		this(understood, null, rooms);
	}

	static AskResult rejected(Rejection rejection) {
		return new AskResult(false, rejection, null);
	}

}

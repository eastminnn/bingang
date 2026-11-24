package com.bingang.room.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 강의실 하나의 하루 수업. 겹치거나 맞닿은 수업은 하나로 합쳐 둔다 */
public final class DaySchedule {

	private final List<Session> sessions;

	private DaySchedule(List<Session> sessions) {
		this.sessions = sessions;
	}

	public static DaySchedule of(List<Session> sessions) {
		return new DaySchedule(merge(sessions));
	}

	public List<Session> sessions() {
		return sessions;
	}

	/** start <= t < end 인 수업이 있으면 사용 중이다. 수업이 끝나는 정각은 비어 있다 */
	public RoomState stateAt(LocalTime time) {
		for (Session session : sessions) {
			if (session.contains(time)) {
				return RoomState.busyUntil(session.end());
			}
			if (session.start().isAfter(time)) {
				return RoomState.free(session.start());
			}
		}
		return RoomState.free(null);
	}

	/** [start, end) 동안 겹치는 수업이 하나도 없으면 그 구간 내내 비어 있다 (물어보기, #74) */
	public boolean freeBetween(LocalTime start, LocalTime end) {
		return sessions.stream().noneMatch(s -> s.start().isBefore(end) && start.isBefore(s.end()));
	}

	/** [from, to) 안에서 수업이 없는 구간들 (시간 순). 물어보기에서 시간을 말하지 않았을 때 그날 빈 시간대로 쓴다 */
	public List<Session> freeSlots(LocalTime from, LocalTime to) {
		List<Session> free = new ArrayList<>();
		LocalTime cursor = from;
		for (Session session : sessions) {
			if (!session.end().isAfter(cursor)) {
				continue;
			}
			if (!session.start().isBefore(to)) {
				break;
			}
			if (session.start().isAfter(cursor)) {
				free.add(new Session(cursor, session.start()));
			}
			cursor = session.end();
		}
		if (cursor.isBefore(to)) {
			free.add(new Session(cursor, to));
		}
		return List.copyOf(free);
	}

	// 교시 단위로 수집하면 연강이 09:00-09:30, 09:30-10:00 처럼 잘게 들어오므로 이어 붙인다
	private static List<Session> merge(List<Session> sessions) {
		List<Session> sorted = sessions.stream().sorted(Comparator.comparing(Session::start)).toList();
		List<Session> merged = new ArrayList<>();
		for (Session session : sorted) {
			Session last = merged.isEmpty() ? null : merged.getLast();
			if (last != null && !session.start().isAfter(last.end())) {
				LocalTime end = session.end().isAfter(last.end()) ? session.end() : last.end();
				merged.set(merged.size() - 1, new Session(last.start(), end));
			}
			else {
				merged.add(session);
			}
		}
		return List.copyOf(merged);
	}

}

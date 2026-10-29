package com.bingang.room.collect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bingang.room.repository.RoomRepository;

@Component
@ConditionalOnProperty(name = "bingang.timetable.enabled", havingValue = "true", matchIfMissing = true)
public class TimetableScheduler {

	private static final Logger log = LoggerFactory.getLogger(TimetableScheduler.class);

	private final TimetableCollector collector;

	private final YuPortalClient client;

	private final RoomRepository rooms;

	public TimetableScheduler(TimetableCollector collector, YuPortalClient client, RoomRepository rooms) {
		this.collector = collector;
		this.client = client;
		this.rooms = rooms;
	}

	@Scheduled(cron = "${bingang.timetable.cron}", zone = "Asia/Seoul")
	public void collectWeekly() {
		run();
	}

	// 처음 띄웠을 때 강의실이 없으면 다음 주까지 기다리지 않고 바로 한 번 수집한다
	@EventListener(ApplicationReadyEvent.class)
	public void collectIfEmpty() {
		if (rooms.count() == 0) {
			run();
		}
	}

	private void run() {
		if (!client.hasCredentials()) {
			log.warn("학교 계정 정보(YU_USERNO, YU_PASSWD)가 없어 시간표 수집을 건너뜁니다");
			return;
		}
		try {
			TimetableCollector.Result result = collector.collect();
			log.info("시간표 수집 완료: buildings={}, rooms={}, sessions={}, removedRooms={}, unusedRooms={}",
					result.buildings(), result.rooms(), result.sessions(), result.removedRooms(), result.unusedRooms());
		}
		catch (Exception e) {
			log.error("시간표 수집 실패, 기존 데이터를 유지합니다", e);
		}
	}

}

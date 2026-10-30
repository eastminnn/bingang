package com.bingang.calendar.collect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bingang.calendar.repository.AcademicEventRepository;
import com.bingang.common.monitoring.CollectorMonitor;

@Component
@ConditionalOnProperty(name = "bingang.calendar.enabled", havingValue = "true", matchIfMissing = true)
public class CalendarScheduler {

	private static final Logger log = LoggerFactory.getLogger(CalendarScheduler.class);

	private final CalendarCollector collector;

	private final AcademicEventRepository events;

	private final CollectorMonitor monitor;

	public CalendarScheduler(CalendarCollector collector, AcademicEventRepository events, CollectorMonitor monitor) {
		this.collector = collector;
		this.events = events;
		this.monitor = monitor;
	}

	@Scheduled(cron = "${bingang.calendar.cron}", zone = "Asia/Seoul")
	public void collectDaily() {
		run();
	}

	// 처음 띄웠을 때 데이터가 없으면 다음 날까지 기다리지 않고 바로 한 번 수집한다
	@EventListener(ApplicationReadyEvent.class)
	public void collectIfEmpty() {
		if (events.count() == 0) {
			run();
		}
	}

	private void run() {
		try {
			CalendarCollector.Result result = monitor.run(CollectorMonitor.CALENDAR, collector::collect,
					CalendarCollector.Result::total);
			log.info("학사 일정 수집 완료: total={}, inserted={}, updated={}", result.total(), result.inserted(),
					result.updated());
		}
		catch (Exception e) {
			log.error("학사 일정 수집 실패, 기존 데이터를 유지합니다", e);
		}
	}

}

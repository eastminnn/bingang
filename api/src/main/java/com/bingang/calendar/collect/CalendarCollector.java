package com.bingang.calendar.collect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bingang.calendar.entity.AcademicEvent;
import com.bingang.calendar.repository.AcademicEventRepository;

@Service
public class CalendarCollector {

	private final YuCalendarClient client;

	private final CalendarParser parser;

	private final AcademicEventRepository events;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public CalendarCollector(YuCalendarClient client, CalendarParser parser, AcademicEventRepository events,
			TransactionTemplate transaction, Clock clock) {
		this.client = client;
		this.parser = parser;
		this.events = events;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 학교 페이지를 받아 articleNo 기준으로 저장한다. 받기·해석에 실패하면 예외를 던지고 기존 데이터는 그대로 둔다.
	 */
	public Result collect() {
		// 외부 요청 동안 DB 커넥션을 잡지 않도록 받기·해석은 트랜잭션 밖에서 한다
		List<CalendarItem> items = parser.parse(client.fetchHtml());
		if (items.isEmpty()) {
			throw new IllegalStateException("학사 일정이 0건입니다. 페이지 구조가 바뀌었을 수 있습니다.");
		}
		return transaction.execute(status -> upsert(items));
	}

	private Result upsert(List<CalendarItem> items) {
		Instant now = Instant.now(clock);
		Map<Long, AcademicEvent> existing = events.findBySourceIdIn(items.stream().map(CalendarItem::sourceId).toList())
			.stream()
			.collect(Collectors.toMap(AcademicEvent::getSourceId, Function.identity()));

		int inserted = 0;
		for (CalendarItem item : items) {
			AcademicEvent event = existing.get(item.sourceId());
			if (event == null) {
				events.save(new AcademicEvent(item.sourceId(), item.startDate(), item.endDate(), item.title(),
						item.substituteDay(), now));
				inserted++;
			}
			else {
				event.update(item.startDate(), item.endDate(), item.title(), item.substituteDay(), now);
			}
		}
		return new Result(items.size(), inserted, items.size() - inserted);
	}

	public record Result(int total, int inserted, int updated) {
	}

}

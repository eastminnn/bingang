package com.bingang.calendar.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.DayOfWeek;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.bingang.TestcontainersConfiguration;
import com.bingang.calendar.repository.AcademicEventRepository;
import com.bingang.common.config.ClockConfig;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, CalendarCollector.class, CalendarParser.class, ClockConfig.class })
class CalendarCollectorTest {

	@MockitoBean
	YuCalendarClient client;

	@Autowired
	CalendarCollector collector;

	@Autowired
	AcademicEventRepository events;

	@Autowired
	EntityManager em;

	@Test
	void 처음_수집하면_모두_저장한다() {
		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture());

		CalendarCollector.Result result = collector.collect();

		assertThat(result).isEqualTo(new CalendarCollector.Result(7, 7, 0));
		assertThat(events.findBySourceId(90004L).orElseThrow().getSubstituteDay()).isEqualTo(DayOfWeek.THURSDAY);
	}

	@Test
	void 다시_수집하면_같은_articleNo는_덮어쓴다() {
		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture());
		collector.collect();
		em.flush();

		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture().replace("\"중간시험\"", "\"중간시험(변경)\""));
		CalendarCollector.Result result = collector.collect();
		em.flush();
		em.clear();

		assertThat(result).isEqualTo(new CalendarCollector.Result(7, 0, 7));
		assertThat(events.count()).isEqualTo(7);
		assertThat(events.findBySourceId(90003L).orElseThrow().getTitle()).isEqualTo("중간시험(변경)");
	}

	@Test
	void 원본_ID가_변경되면_동일한_이전_일정만_제거한다() {
		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture());
		collector.collect();
		em.flush();

		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture().replace("90003", "99003"));
		collector.collect();
		em.flush();
		em.clear();

		assertThat(events.count()).isEqualTo(7);
		assertThat(events.findBySourceId(90003L)).isEmpty();
		assertThat(events.findBySourceId(99003L)).isPresent();
		// 다시 수집해도 중복되지 않는다.
		collector.collect();
		assertThat(events.count()).isEqualTo(7);
	}

	@Test
	void 수집_결과에_없는_다른_일정은_보존한다() {
		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture());
		collector.collect();
		em.flush();

		given(client.fetchHtml()).willReturn("<script>var calData = [" +
			"{articleNo:99003,startDt:'2026-10-20',endDt:'2026-10-26',text:'별도 일정'}" +
			"];</script>");
		collector.collect();
		em.flush();
		em.clear();

		assertThat(events.count()).isEqualTo(8);
		assertThat(events.findBySourceId(90003L)).isPresent();
	}

	@Test
	void 학교_페이지를_못_받으면_기존_데이터를_유지한다() {
		given(client.fetchHtml()).willReturn(CalendarParserTest.fixture());
		collector.collect();
		em.flush();

		given(client.fetchHtml()).willThrow(new ResourceAccessException("timeout"));

		assertThatThrownBy(collector::collect).isInstanceOf(ResourceAccessException.class);
		assertThat(events.count()).isEqualTo(7);
	}

	@Test
	void 일정이_0건이면_저장하지_않고_실패로_본다() {
		given(client.fetchHtml()).willReturn("<script>var calData = [];</script>");

		assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class);
		assertThat(events.count()).isZero();
	}

}

package com.bingang;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
// 테스트 중에 학교 사이트로 요청이 나가지 않도록 수집 스케줄러들을 끈다
@SpringBootTest(properties = { "bingang.calendar.enabled=false", "bingang.timetable.enabled=false" })
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.bingang.room.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class FreeRoomParserTest {

	@Test
	void 강의실_번호에서_건물_코드와_호수를_나눈다() {
		List<FreeRoom> rooms = FreeRoomParser.parse(fixture());

		// 번호 없는 방은 건너뛴다
		assertThat(rooms).hasSize(3);
		assertThat(rooms.getFirst()).isEqualTo(new FreeRoom("B03", "인문관", "201", "강의실", "일반강의실", 60));
		assertThat(rooms).extracting(FreeRoom::key).containsExactly("B03-201", "B03-B101", "C03-1012");
	}

	@Test
	void 빈_값은_null로_공백은_지운다() {
		List<FreeRoom> rooms = FreeRoomParser.parse(fixture());

		assertThat(rooms.get(1).capacity()).isNull();
		assertThat(rooms.get(2).buildingName()).isEqualTo("천마관");
		assertThat(rooms.get(2).capacity()).isEqualTo(120);
	}

	@Test
	void 로그인이_풀려_HTML이_오면_예외를_던진다() {
		assertThatThrownBy(() -> FreeRoomParser.parse("<html><script>location.href='/login'</script></html>"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("로그인");
	}

	@Test
	void result7이_없으면_예외를_던진다() {
		assertThatThrownBy(() -> FreeRoomParser.parse("{\"error\":\"세션이 만료되었습니다\"}"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("result7");
	}

	static String fixture() {
		try (InputStream in = FreeRoomParserTest.class.getResourceAsStream("/fixtures/empty-rooms.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}

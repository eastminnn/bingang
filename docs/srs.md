# 빈강 요구사항 명세 (SRS)

> 범위: 1단계 앱 MVP · 최종 수정 2026-10-28

## 1. 개요

학교의 **지금 비어 있는 강의실**을 보여 주는 웹 서비스. 실사용자가 있는 서비스를 운영하며 인프라·모니터링을 연습하는 것이 목적이다.

| 단계 | 내용 |
|---|---|
| **1. 앱 MVP** | API + DB + 화면, 로컬 Docker Compose, 가짜 데이터 |
| 2. 배포 | Oracle Cloud A1(arm64), Caddy HTTPS, GitHub Actions |
| 3. 모니터링 | Prometheus · Grafana · Loki, 디스코드 알림 |
| 4. 실데이터 | 학교·학생회 허락 후 연동 |

## 2. 기능 요구사항

| ID | 요구사항 |
|---|---|
| F-1 | 현재 시각 기준 빈 강의실 목록을 보여 준다 |
| F-2 | 건물별로 필터링할 수 있다 |
| F-3 | 각 강의실에 다음 수업 시작 시각("16:00까지")을 표시한다. 오늘 남은 수업이 없으면 "오늘 종일" |
| F-4 | 오래 비는 강의실이 위에 오도록 정렬한다 ("오늘 종일"이 맨 위) |
| F-5 | 화면은 1분마다 자동 갱신한다 (탭이 백그라운드면 중지) |
| F-6 | 첫 진입 시 스플래시를 보여 준다 (`design/splash.html`) |

**"비어 있음" 판정**: 시각 `t`·요일 `d`에 `start_time <= t < end_time`인 수업이 없으면 빈 강의실. 종료 정각은 비어 있음. 주말·수업 없는 시간은 모두 비어 있음. 시각과 요일은 항상 `Asia/Seoul` 기준.

### 제외 (이후)

요일·시간대 선택 화면, 강의실 상세 시간표, 로그인·즐겨찾기, 공휴일·방학·건물 운영 시간.

## 3. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/buildings` | 건물 목록 |
| GET | `/api/rooms/available?buildingId=&at=` | 빈 강의실. `buildingId`·`at`(ISO-8601) 생략 가능, `at` 생략 시 현재 시각 |
| GET | `/actuator/health` | 헬스 체크 |

```json
// GET /api/rooms/available
{
  "at": "2026-10-28T14:30:00+09:00",
  "rooms": [
    { "roomId": 15, "building": "공학관", "number": "402", "freeUntil": null },
    { "roomId": 12, "building": "공학관", "number": "301", "freeUntil": "16:00" }
  ]
}
```

오류: 잘못된 파라미터 `400`, 없는 건물 `404` (ProblemDetail 형식).

## 4. 비기능 요구사항

| ID | 요구사항 |
|---|---|
| N-1 | `docker compose up -d` 한 번으로 전체 실행 |
| N-2 | arm64(Mac M3, Oracle A1)에서 동작 |
| N-3 | 모바일 우선(360px), 다크 모드 지원, 스플래시 시안의 디자인 토큰 사용 |
| N-4 | 비밀값은 `.env`로 분리, 저장소에는 `.env.example`만 커밋 |
| N-5 | 판정 로직은 단위 테스트, API·DB는 Testcontainers 통합 테스트 |
| N-6 | 실데이터 연동 전까지 가짜 데이터만 사용, 저장소 비공개 |

## 5. 기술 스택

Java 21 · Spring Boot · Spring Data JPA · Flyway · PostgreSQL · Caddy · 정적 HTML/CSS/JS · Docker Compose

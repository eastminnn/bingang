# 빈강 1단계(앱 MVP) 설계

- 작성일: 2026-10-28
- 상태: 검토 중

## 1. 배경과 목표

빈강은 학교의 "지금 비어 있는 강의실"을 보여 주는 서비스다. 기능 자체보다 **실제 사용자가 있는 서비스를 직접 운영하며 인프라·모니터링을 연습하는 것**이 진짜 목적이다.

전체 작업은 4단계로 나눈다. 이 문서는 1단계만 다룬다.

| 단계 | 내용 | 비고 |
|---|---|---|
| **1. 앱 MVP (이 문서)** | API + DB + 화면을 로컬 Docker Compose로 실행 | 가짜 데이터 |
| 2. 배포 | Oracle Cloud A1(arm64) + Caddy HTTPS + GitHub Actions 자동 배포 | |
| 3. 모니터링 | Prometheus · Grafana · Loki + 디스코드 알림 | |
| 4. 실데이터 | 학생종합정보시스템 데이터 연동 | 학교·학생회 허락 후 |

### 1단계 완료 기준

1. 저장소 루트에서 `docker compose up -d` 한 번으로 모든 컨테이너가 뜬다.
2. 브라우저에서 `http://localhost`를 열면 스플래시가 나온 뒤 지금 빈 강의실 목록이 보인다.
3. 건물 필터와 "○○:○○까지 비어 있음" 표시가 동작한다.
4. `./gradlew test`가 통과한다.
5. 맥(arm64)에서 빌드한 이미지가 그대로 A1(arm64)에서도 돌 수 있다. 실제 확인은 2단계에서 한다.

## 2. 범위

### 포함

- 지금(또는 지정한 시각에) 빈 강의실 목록 조회
- 건물 필터
- 강의실마다 "다음 수업 시작 전까지 비어 있음" 시각 표시 (오늘 남은 수업이 없으면 "오늘 종일")
- 스플래시 화면 (`design/splash.html` 시안 기반)
- 가짜 시간표 시드 데이터
- 헬스 체크 엔드포인트 (`/actuator/health`). 2·3단계에서 사용한다.

### 제외 (이후 단계로 미룸)

- 원하는 요일·시간대를 고르는 화면 (API는 `at` 파라미터로 미리 지원)
- 강의실 상세(하루 시간표)
- 로그인, 즐겨찾기, 공휴일·방학·시험 기간 처리
- 서버 배포, HTTPS, 모니터링 → 2·3단계

## 3. 아키텍처

```
브라우저 ──► Caddy :80 ──┬─ /            → 정적 파일 (web/)
                         └─ /api/*, /actuator/health → api:8080 (Spring Boot)
                                                          │
                                                          ▼
                                                     db:5432 (PostgreSQL)
```

- **Caddy**: 웹 서버이자 리버스 프록시(앞에서 요청을 받아 뒤쪽 서버로 넘겨 주는 서버). 1단계에서는 HTTP로만 쓰고, 2단계에서 도메인을 붙이면 HTTPS 인증서를 자동으로 발급받는다. Nginx보다 설정이 짧아서 선택했다.
- **api**: Spring Boot. 외부로 포트를 열지 않고 Caddy를 통해서만 접근한다.
- **db**: PostgreSQL. 데이터는 이름 있는 볼륨에 저장해서 컨테이너를 지웠다 다시 만들어도 남는다.
- 모든 이미지는 arm64를 지원하는 공식 이미지를 쓴다 (`caddy`, `postgres`, `eclipse-temurin`).

### 저장소 구조

```
bingang/
├── api/                  # Spring Boot (Gradle Kotlin DSL)
├── web/                  # 정적 HTML/CSS/JS
├── design/               # 디자인 시안
├── docs/design/          # 설계 문서
├── compose.yaml
├── Caddyfile
├── .env.example          # DB 비밀번호 등 환경 변수 예시 (.env는 커밋하지 않음)
└── README.md
```

## 4. 데이터 모델

```
building (건물)
  id          bigint PK
  code        varchar  UNIQUE   -- 예: "ENG"
  name        varchar           -- 예: "공학관"

room (강의실)
  id          bigint PK
  building_id bigint FK → building
  number      varchar           -- 예: "301"
  capacity    int     NULL
  UNIQUE (building_id, number)

class_session (수업이 강의실을 쓰는 시간)
  id          bigint PK
  room_id     bigint FK → room
  course_name varchar
  day_of_week smallint          -- 1=월 … 7=일 (ISO-8601)
  start_time  time
  end_time    time
  CHECK (start_time < end_time)
  INDEX (room_id, day_of_week)
```

- 시간은 교시가 아니라 `time`(시:분)으로 저장한다. 학교마다 교시 길이가 달라서, 실데이터를 붙일 때 변환만 하면 된다.
- 학기 구분은 1단계에서 넣지 않는다. 데이터에는 "현재 학기" 하나만 있다고 가정한다.
- 스키마는 **Flyway**로 관리한다. SQL 파일에 버전 번호를 붙여 두면 앱이 시작할 때 아직 적용되지 않은 파일만 순서대로 실행해 준다.
  - `db/migration/V1__init.sql`: 테이블 생성
  - `db/seed/V100__fake_timetable.sql`: 가짜 데이터. `local` 프로필에서만 읽는다.

### 가짜 데이터

건물 3개, 강의실 약 20개, 월~금 09:00–18:00 사이 수업을 넣는다. 수업은 1시간 15분 또는 3시간 단위로 하고, 강의실 이용률은 60~70% 정도로 맞춘다. 그래야 어느 시각에 봐도 빈 방과 찬 방이 섞여 나온다. 건물명과 과목명은 실제 학교와 무관한 이름을 쓴다.

## 5. API

### `GET /api/buildings`

```json
[{ "id": 1, "code": "ENG", "name": "공학관" }]
```

### `GET /api/rooms/available?buildingId={id}&at={ISO-8601 시각}`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `buildingId` | 아니오 | 없으면 전체 건물 |
| `at` | 아니오 | 없으면 현재 시각(Asia/Seoul). 예: `2026-10-28T14:30:00+09:00` |

```json
{
  "at": "2026-10-28T14:30:00+09:00",
  "rooms": [
    { "roomId": 12, "building": "공학관", "number": "301",
      "freeUntil": "16:00" },
    { "roomId": 15, "building": "공학관", "number": "402",
      "freeUntil": null }
  ]
}
```

- `freeUntil`: 오늘 다음 수업이 시작하는 시각이다. 오늘 남은 수업이 없으면 `null`이고, 화면에는 "오늘 종일"로 표시한다.
- 정렬: `freeUntil`이 늦은 순(오래 비는 방이 위), `null`이 맨 위.
- 오류: 잘못된 파라미터는 `400`, 없는 건물은 `404`. 응답 본문은 Spring 기본 `ProblemDetail`(RFC 9457) 형식이다.

### 판정 규칙 ("비어 있음"의 정의)

시각 `t`, 요일 `d`일 때 강의실이 비어 있으려면, `day_of_week = d` 이고 `start_time <= t < end_time`인 수업이 하나도 없어야 한다.

- 수업이 끝나는 시각 정각에는 비어 있다고 본다 (`end_time` 제외).
- 주말이나 수업이 없는 시간에는 모든 강의실이 비어 있다. 운영 시간(건물 개방 시간)은 1단계에서 다루지 않는다.

### 시간대

서버(컨테이너)는 UTC로 돈다. 그래서 "지금"과 "오늘 요일"은 반드시 `Asia/Seoul`로 계산한다. 이를 위해 `Clock` 빈을 `Asia/Seoul`로 등록하고 코드에서 주입받는다. 테스트에서는 고정 시각 `Clock`으로 바꿔 끼운다.

## 6. 백엔드 구성

- Java 21, Spring Boot 최신 안정 버전 (구현 시점에 확인), Gradle Kotlin DSL
- 의존성: Spring Web, Spring Data JPA, PostgreSQL 드라이버, Flyway, Actuator, Validation
- 패키지는 기능 단위로 나눈다:
  - `building`: 엔티티, 리포지토리, 컨트롤러
  - `room`: 엔티티, 리포지토리, `AvailabilityService`, 컨트롤러
  - `config`: `Clock` 빈 등
- **판정 로직은 `AvailabilityService` 한 곳에 모은다.** 입력은 (시각, 건물)이고 출력은 (빈 방, freeUntil) 목록이다. 이 서비스를 테스트로 꼼꼼히 검증한다.
- 프로필은 `local`(가짜 시드 포함)과 `prod`(시드 없음) 두 개다. 2단계에서 `prod`를 쓴다.
- Docker 이미지: 멀티 스테이지 빌드(빌드용 컨테이너에서 jar를 만들고, 실행용 이미지에는 jar만 복사해서 이미지를 작게 유지). 베이스는 `eclipse-temurin:21-jre`.

## 7. 프론트엔드 구성

- `web/index.html`, `web/style.css`, `web/app.js`. 빌드 도구는 쓰지 않는다.
- 디자인 토큰은 스플래시 시안을 그대로 이어받는다: Pretendard, `--bg`·`--ink`·`--free` 등의 색, 다크 모드 지원.
- 화면 흐름:
  1. 스플래시 애니메이션 (약 2초. 첫 방문에만 전체 재생하고, 이후에는 짧게)
  2. 상단: 현재 시각 + 건물 필터(칩 형태 버튼)
  3. 목록: `공학관 301 · 16:00까지` 형태의 카드. 오래 비는 방이 위에 온다.
- 1분마다 자동으로 새로 고친다. 탭이 백그라운드에 있으면 멈춘다.
- 상태별 화면: 로딩(스켈레톤), 빈 결과("지금 빈 강의실이 없어요"), 오류("잠시 후 다시 시도해 주세요" + 다시 시도 버튼)
- 모바일 우선(폭 360px 기준). 데스크톱에서는 가운데 정렬로 폭을 제한한다.

## 8. 테스트 전략

| 종류 | 대상 | 도구 |
|---|---|---|
| 단위 | `AvailabilityService` 판정 규칙: 경계 시각(시작 정각, 종료 정각), 주말, 마지막 수업 이후(`freeUntil = null`), 건물 필터 | JUnit 5, 고정 `Clock` |
| 통합 | 리포지토리 쿼리 + API 응답 형식, Flyway 마이그레이션 적용 | Spring Boot Test + **Testcontainers** (테스트할 때만 진짜 PostgreSQL 컨테이너를 잠깐 띄워 주는 라이브러리) |
| 수동 | Compose 전체 실행 → 브라우저 확인, `curl`로 API 확인 | README에 명령어 정리 |

판정 규칙은 TDD로 개발한다(테스트를 먼저 쓰고 구현).

## 9. 설정과 비밀값

- DB 계정과 비밀번호는 `.env`에 둔다. `.env`는 `.gitignore`에 넣고, 저장소에는 `.env.example`만 커밋한다.
- Compose가 `.env`를 읽어 컨테이너 환경 변수로 넘긴다.

## 10. Git 작업 방식

- 저장소는 GitHub **비공개(private)**로 만든다.
- `main`에는 직접 커밋하지 않는다. 기능마다 브랜치(`feat/...`, `docs/...`)를 만들고 PR로 머지한다.
- 커밋 메시지는 한국어 또는 영어 Conventional Commits 형식(`feat:`, `fix:`, `docs:` …)으로 쓴다.

## 11. 열린 질문 (1단계를 막지는 않음)

- 실제 학교의 건물·강의실 목록과 교시 시간표 → 4단계에서 확인
- 서비스 도메인 이름 → 2단계 전까지 결정

<p align="center"><img src="design/logo/bingang-wordmark.png" alt="빈강" width="360"></p>

<p align="center">영남대학교의 <b>지금 비어 있는 강의실</b>과 언제까지 비는지를 알려 주는 웹 서비스</p>
<p align="center"><a href="https://bingang.duckdns.org">bingang.duckdns.org</a></p>

## 기능

- 건물을 고르면 빈 강의실만 호수 순서·층별로 보여 주고, `11:30까지 비어 있어요`처럼 남은 시간을 알려 준다
- 강의실마다 09–21시 타임라인, 다른 요일·시각 미리 보기
- 보강일(공휴일 수업 대체일)에는 지정된 요일의 시간표로 판정
- 학사 일정 탭, 즐겨찾기·최근 본 건물, 다크 모드, 로그인 없음

## 구조

```
브라우저 ──HTTPS──▶ Caddy ─┬─ /api/* ─▶ Spring Boot ─▶ PostgreSQL
                            └─ 그 외  ─▶ 정적 화면(web/index.html)

Spring Boot 안의 수집기
  - 학사 일정: 학교 홈페이지 calData, 매일 05:00
  - 시간표: 학생종합정보시스템 빈강의실조회, 매주 월 04:00 (요일 6 × 시간 칸 24, 1초에 1건)
```

사용자 요청은 우리 DB만 조회하고 학교 시스템으로 넘기지 않는다. 수집이 실패하면 마지막 데이터를 유지한다.

## 기술

Java 21 · Spring Boot 4 · Spring Data JPA · Flyway · PostgreSQL 17 · Caddy · Docker Compose · GitHub Actions · Oracle Cloud A1 (arm64)

## 디렉터리

```
api/        Spring Boot (도메인별 building · room · calendar, 공통 common)
web/        화면 index.html과 Caddy 이미지
docs/       요구사항(srs.md), ERD(erd.md)
design/logo 로고
.github/    이슈·PR 템플릿, 테스트·배포 워크플로
```

## 로컬 실행

```bash
cp .env.example .env          # DB 비밀번호 등. 시간표 수집에는 학교 계정이 필요
docker compose up -d --build
open http://localhost
```

테스트는 Docker가 켜진 상태에서 `cd api && ./gradlew test` (Testcontainers로 PostgreSQL을 띄운다).

## 배포

`main`에 머지하면 GitHub Actions가 테스트 후 서버에 코드를 보내 `docker compose up -d --build`로 다시 띄우고 외부에서 헬스 체크를 한다.

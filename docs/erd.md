# 빈강 ERD

> 최종 수정 2026-10-28

```mermaid
erDiagram
    building ||--o{ room : has
    room ||--o{ room_schedule : "used at"

    building {
        bigint id PK
        varchar code UK "예: E21"
        varchar name "예: IT관"
        char zone "A~G (code 첫 글자)"
        boolean visible "목록 노출 여부"
    }
    room {
        bigint id PK
        bigint building_id FK
        varchar number "예: 301"
        smallint floor
        int capacity "NULL 허용"
    }
    room_schedule {
        bigint id PK
        bigint room_id FK
        smallint day_of_week "1=월 ~ 7=일"
        time start_time
        time end_time
        varchar label "과목명 등, NULL 허용"
    }
    academic_event {
        bigint id PK
        bigint source_id UK "학교 articleNo"
        date start_date
        date end_date
        varchar title "원문 그대로"
        smallint substitute_day "보강일이면 적용할 요일, 아니면 NULL"
        timestamptz fetched_at
    }
```

## 제약

- `room`: `UNIQUE (building_id, number)`
- `room_schedule`: `CHECK (start_time < end_time)`, `INDEX (room_id, day_of_week)`
- `academic_event`: `CHECK (start_date <= end_date)`, `INDEX (start_date)`

## 메모

- `room_schedule`은 "강의실이 언제 사용 중인가"만 담는다. 과목 정보는 `label` 하나로 충분하다.
- 시간은 교시가 아니라 `time`으로 저장한다.
- 학기 구분은 없다. 학기가 바뀌면 시간표를 교체한다.
- `academic_event.substitute_day`는 수집할 때 `공휴일수업대체지정일(… (목) …)`을 해석해서 채운다. 판정할 때는 그 날짜에 값이 있으면 그 요일의 시간표를 쓴다.
- `building.visible`: 시간표가 있어도 목록에서 뺄 건물(예: 중앙도서관)을 숨긴다.
- 스키마는 Flyway로 관리하고, 가짜 시간표 시드는 `local` 프로필에서만 적용한다.

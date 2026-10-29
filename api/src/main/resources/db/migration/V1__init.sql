-- 건물: code는 학교 시스템의 강의동 코드
CREATE TABLE building (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code    VARCHAR(10)  NOT NULL UNIQUE,
    name    VARCHAR(50)  NOT NULL,
    zone    CHAR(1)      NOT NULL,
    visible BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE room (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    building_id BIGINT      NOT NULL REFERENCES building (id),
    number      VARCHAR(20) NOT NULL,
    floor       SMALLINT    NOT NULL,
    capacity    INT,
    UNIQUE (building_id, number)
);

-- 강의실이 사용 중인 시간 (1=월 ~ 7=일)
CREATE TABLE room_schedule (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id     BIGINT   NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL,
    label       VARCHAR(100),
    CHECK (start_time < end_time)
);
CREATE INDEX idx_room_schedule_room_day ON room_schedule (room_id, day_of_week);

-- 학사 일정: source_id는 학교 페이지의 articleNo
CREATE TABLE academic_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id      BIGINT       NOT NULL UNIQUE,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    title          VARCHAR(200) NOT NULL,
    substitute_day SMALLINT CHECK (substitute_day BETWEEN 1 AND 7),
    fetched_at     TIMESTAMPTZ  NOT NULL,
    CHECK (start_date <= end_date)
);
CREATE INDEX idx_academic_event_start ON academic_event (start_date);

-- 수집기별 마지막 실행 결과. 앱을 다시 띄워도 "마지막 성공 시각"을 알 수 있도록 DB에 둔다
CREATE TABLE collector_status (
    name             VARCHAR(30) PRIMARY KEY,
    last_success_at  TIMESTAMPTZ,
    last_failure_at  TIMESTAMPTZ,
    last_items       INT,
    last_duration_ms BIGINT
);

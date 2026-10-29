-- 학교 시스템 빈강의실조회 응답(ltRoomNm, ltRoomType1)에 맞춘 보정
ALTER TABLE room ADD COLUMN name VARCHAR(100);
ALTER TABLE room ADD COLUMN type VARCHAR(50);

-- 호수만으로 층을 알 수 없는 방(강당 등)이 있어 NULL 허용
ALTER TABLE room ALTER COLUMN floor DROP NOT NULL;

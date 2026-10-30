-- 운영에서 확인한 이전/재등록 ID 쌍 중 내용이 같은 항목만 정리한다.
DELETE FROM academic_event old_event
USING academic_event current_event
WHERE current_event.source_id BETWEEN 232290604 AND 232290670
  AND old_event.source_id = current_event.source_id - 14182
  AND old_event.start_date = current_event.start_date
  AND old_event.end_date = current_event.end_date
  AND old_event.title = current_event.title
  AND old_event.substitute_day IS NOT DISTINCT FROM current_event.substitute_day;

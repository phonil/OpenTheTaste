-- is_standalone: 홈 화면 노출 대상 여부 (TRUE = SERIES 또는 단독 CONTENTS)
-- 기존 isDisplayable() 서브쿼리(OR + NOT EXISTS)를 대체하기 위한 반정규화 컬럼

ALTER TABLE media ADD COLUMN is_standalone BOOLEAN NOT NULL;

-- SERIES -> TRUE
UPDATE media SET is_standalone = TRUE WHERE media_type = 'SERIES';

-- 단독 CONTENTS (시리즈 미소속) -> TRUE
UPDATE media m
JOIN contents c ON c.media_id = m.id
SET m.is_standalone = TRUE
WHERE m.media_type = 'CONTENTS' AND c.series_id IS NULL;

-- 에피소드 CONTENTS (시리즈 소속) -> FALSE
UPDATE media m
JOIN contents c ON c.media_id = m.id
SET m.is_standalone = FALSE
WHERE m.media_type = 'CONTENTS' AND c.series_id IS NOT NULL;

-- SHORT_FORM -> FALSE
UPDATE media SET is_standalone = FALSE WHERE media_type = 'SHORT_FORM';

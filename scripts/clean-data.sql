-- =============================================
-- 테스트 데이터 전체 삭제 (메타데이터 유지)
-- 사용법: mysql -u ott -p ott < scripts/clean-data.sql
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE member_mood_refresh;
TRUNCATE TABLE click_event;
TRUNCATE TABLE playback;
TRUNCATE TABLE watch_history;
TRUNCATE TABLE comment;
TRUNCATE TABLE likes;
TRUNCATE TABLE bookmark;
TRUNCATE TABLE media_metrics;
TRUNCATE TABLE media_mood_tag;
TRUNCATE TABLE media_tag;
TRUNCATE TABLE short_form;
TRUNCATE TABLE contents;
TRUNCATE TABLE series;
TRUNCATE TABLE media;
TRUNCATE TABLE preferred_tag;
TRUNCATE TABLE member_radar_preference;
TRUNCATE TABLE member;

-- 유지: category, tag, mood_category, mood_tag

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'Clean completed. Metadata tables (category, tag, mood_category, mood_tag) preserved.' AS result;

-- Trending API용 복합 인덱스
-- WHERE (status, public_status, media_status, is_standalone) 등호 조건 4개로 범위 확정
-- ORDER BY bookmark_count DESC → 인덱스 순회로 filesort 제거
CREATE INDEX idx_media_trending
ON media (status, public_status, media_status, is_standalone, bookmark_count DESC);

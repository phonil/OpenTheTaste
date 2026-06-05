-- =============================================
-- OTT 테스트 데이터 시드 프로시저
-- 사용법:
--   mysql -u ott -p ott < scripts/seed-procedures.sql
--   mysql -u ott -p ott -e "CALL seed_all('medium');"
-- =============================================

DELIMITER //

DROP PROCEDURE IF EXISTS seed_all //

CREATE PROCEDURE seed_all(IN p_preset VARCHAR(10))
proc_body: BEGIN

    -- =============================================
    -- CONFIGURATION
    -- =============================================
    IF p_preset NOT IN ('small', 'medium', 'large', 'xl') THEN
        SELECT CONCAT('ERROR: Unknown preset "', p_preset, '". Use: small, medium, large, xl') AS error;
        LEAVE proc_body;
    END IF;

    -- Verify metadata
    IF (SELECT COUNT(*) FROM category) < 4 OR (SELECT COUNT(*) FROM tag) < 28 THEN
        SELECT 'ERROR: Run seed-metadata.sql first!' AS error;
        LEAVE proc_body;
    END IF;

    -- Base counts
    IF p_preset = 'small' THEN
        SET @v_member = 100, @v_series = 100, @v_contents = 600, @v_sf = 300;
        SET @v_bookmark = 5000, @v_likes = 5000, @v_comment = 3000;
        SET @v_watch = 10000, @v_playback = 5000, @v_click = 3000;
    ELSEIF p_preset = 'medium' THEN
        SET @v_member = 1000, @v_series = 1000, @v_contents = 6000, @v_sf = 3000;
        SET @v_bookmark = 50000, @v_likes = 50000, @v_comment = 30000;
        SET @v_watch = 100000, @v_playback = 50000, @v_click = 30000;
    ELSEIF p_preset = 'large' THEN
        SET @v_member = 10000, @v_series = 5000, @v_contents = 30000, @v_sf = 15000;
        SET @v_bookmark = 200000, @v_likes = 200000, @v_comment = 100000;
        SET @v_watch = 500000, @v_playback = 200000, @v_click = 100000;
    ELSEIF p_preset = 'xl' THEN
        SET @v_member = 10000, @v_series = 50000, @v_contents = 300000, @v_sf = 150000;
        SET @v_bookmark = 2000000, @v_likes = 2000000, @v_comment = 1000000;
        SET @v_watch = 5000000, @v_playback = 2000000, @v_click = 1000000;
    END IF;

    SET @v_media = @v_series + @v_contents + @v_sf;

    -- =============================================
    -- SETUP: Sequence table (1..10000, cross join for larger)
    -- =============================================
    DROP TABLE IF EXISTS _seed_seq;
    CREATE TABLE _seed_seq (n INT UNSIGNED NOT NULL PRIMARY KEY);
    SET @@cte_max_recursion_depth = 10000;
    INSERT INTO _seed_seq
        WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
        SELECT n FROM seq;

    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET @start_time = NOW();

    SELECT CONCAT('Starting seed: preset=', p_preset, ', members=', @v_member, ', media=', @v_media) AS progress;

    -- =============================================
    -- 1. MEMBER
    -- =============================================
    INSERT INTO member (email, password, nickname, role, provider, provider_id, refresh_token, onboarding_completed, created_date, modified_date, status)
    SELECT
        CONCAT('testuser', rn, '@test.com'),
        NULL,
        CONCAT('TestUser', rn),
        CASE
            WHEN rn <= FLOOR(@v_member * 0.90) THEN 'MEMBER'
            WHEN rn <= FLOOR(@v_member * 0.95) THEN 'EDITOR'
            ELSE 'ADMIN'
        END,
        'KAKAO',
        CONCAT('kakao_test_', rn),
        NULL,
        TRUE,
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_member/10000.0)) nums
    WHERE rn <= @v_member;

    SET @m_start = LAST_INSERT_ID();
    SET @m_end = @m_start + @v_member - 1;
    SET @up_start = @m_start + FLOOR(@v_member * 0.90);
    SET @up_count = @v_member - FLOOR(@v_member * 0.90);
    SELECT CONCAT('[1/16] member: ', @v_member) AS progress;

    -- =============================================
    -- 2. MEMBER_RADAR_PREFERENCE (1:1 with member)
    -- =============================================
    INSERT INTO member_radar_preference (member_id, popularity, immersion, mania, recency, re_watch, created_date, modified_date, status)
    SELECT
        @m_start + rn - 1,
        FLOOR(RAND(rn) * 101),
        FLOOR(RAND(rn + 100000) * 101),
        FLOOR(RAND(rn + 200000) * 101),
        FLOOR(RAND(rn + 300000) * 101),
        FLOOR(RAND(rn + 400000) * 101),
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_member/10000.0)) nums
    WHERE rn <= @v_member;

    SELECT CONCAT('[2/16] member_radar_preference: ', @v_member) AS progress;

    -- =============================================
    -- 3. PREFERRED_TAG (4 per member, offsets 0/7/14/21 guarantee uniqueness in 28 tags)
    -- =============================================
    INSERT INTO preferred_tag (member_id, tag_id, created_date, modified_date, status)
    SELECT
        @m_start + nums.rn - 1,
        ((nums.rn - 1 + t.offset) % 28) + 1,
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_member/10000.0)) nums
    CROSS JOIN (SELECT 0 AS offset UNION ALL SELECT 7 UNION ALL SELECT 14 UNION ALL SELECT 21) t
    WHERE nums.rn <= @v_member;

    SELECT CONCAT('[3/16] preferred_tag: ', @v_member * 4) AS progress;

    -- =============================================
    -- 4. MEDIA (SERIES) + SERIES
    -- =============================================
    INSERT INTO media (uploader_id, title, description, poster_url, thumbnail_url, bookmark_count, likes_count, media_type, public_status, media_status, created_date, modified_date, status)
    SELECT
        @up_start + FLOOR(RAND(rn) * @up_count),
        CONCAT('시리즈 ', rn),
        CONCAT('시리즈 ', rn, '의 설명입니다.'),
        CONCAT('/posters/series_', rn, '.jpg'),
        CONCAT('/thumbnails/series_', rn, '.jpg'),
        FLOOR(RAND(rn + 100000) * 200),
        FLOOR(RAND(rn + 200000) * 500),
        'SERIES',
        IF(RAND(rn + 300000) < 0.9, 'PUBLIC', 'PRIVATE'),
        IF(RAND(rn + 400000) < 0.9, 'COMPLETED', 'INIT'),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 500000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_series/10000.0)) nums
    WHERE rn <= @v_series;

    SET @sm_start = LAST_INSERT_ID();

    INSERT INTO series (media_id, actors, created_date, modified_date, status)
    SELECT
        @sm_start + rn - 1,
        CONCAT('배우', FLOOR(RAND(rn) * 50) + 1, ', 배우', FLOOR(RAND(rn + 100000) * 50) + 51),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 500000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_series/10000.0)) nums
    WHERE rn <= @v_series;

    SET @s_start = LAST_INSERT_ID();
    SET @s_count = @v_series;
    SELECT CONCAT('[4/16] media(SERIES) + series: ', @v_series) AS progress;

    -- =============================================
    -- 5. MEDIA (CONTENTS) + CONTENTS
    -- =============================================
    INSERT INTO media (uploader_id, title, description, poster_url, thumbnail_url, bookmark_count, likes_count, media_type, public_status, media_status, created_date, modified_date, status)
    SELECT
        @up_start + FLOOR(RAND(rn + 600000) * @up_count),
        CONCAT('콘텐츠 ', rn),
        CONCAT('콘텐츠 ', rn, '의 설명입니다.'),
        CONCAT('/posters/contents_', rn, '.jpg'),
        CONCAT('/thumbnails/contents_', rn, '.jpg'),
        FLOOR(RAND(rn + 700000) * 300),
        FLOOR(RAND(rn + 800000) * 800),
        'CONTENTS',
        IF(RAND(rn + 900000) < 0.9, 'PUBLIC', 'PRIVATE'),
        IF(RAND(rn + 1000000) < 0.9, 'COMPLETED', 'INIT'),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 1100000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_contents/10000.0)) nums
    WHERE rn <= @v_contents;

    SET @cm_start = LAST_INSERT_ID();

    INSERT INTO contents (media_id, series_id, actors, duration, video_size, origin_url, master_playlist_url, created_date, modified_date, status)
    SELECT
        @cm_start + rn - 1,
        IF(RAND(rn + 1200000) < 0.7, @s_start + FLOOR(RAND(rn + 1300000) * @s_count), NULL),
        CONCAT('배우', FLOOR(RAND(rn + 1400000) * 50) + 1, ', 배우', FLOOR(RAND(rn + 1500000) * 50) + 51),
        IF(RAND(rn + 1600000) < 0.35,
            FLOOR(5400 + RAND(rn + 1700000) * 1800),
            FLOOR(1800 + RAND(rn + 1800000) * 1800)),
        FLOOR(500 + RAND(rn + 1900000) * 2500),
        CONCAT('/videos/contents_', rn, '.mp4'),
        IF(RAND(rn + 1000000) < 0.9, CONCAT('/hls/contents_', rn, '/master.m3u8'), NULL),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 1100000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_contents/10000.0)) nums
    WHERE rn <= @v_contents;

    SET @c_start = LAST_INSERT_ID();
    SET @c_count = @v_contents;
    SELECT CONCAT('[5/16] media(CONTENTS) + contents: ', @v_contents) AS progress;

    -- =============================================
    -- 6. MEDIA (SHORT_FORM) + SHORT_FORM
    -- =============================================
    INSERT INTO media (uploader_id, title, description, poster_url, thumbnail_url, bookmark_count, likes_count, media_type, public_status, media_status, created_date, modified_date, status)
    SELECT
        @up_start + FLOOR(RAND(rn + 2000000) * @up_count),
        CONCAT('숏폼 ', rn),
        CONCAT('숏폼 ', rn, '의 설명입니다.'),
        CONCAT('/posters/short_', rn, '.jpg'),
        NULL,
        FLOOR(RAND(rn + 2100000) * 100),
        FLOOR(RAND(rn + 2200000) * 300),
        'SHORT_FORM',
        IF(RAND(rn + 2300000) < 0.9, 'PUBLIC', 'PRIVATE'),
        IF(RAND(rn + 2400000) < 0.9, 'COMPLETED', 'INIT'),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 2500000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_sf/10000.0)) nums
    WHERE rn <= @v_sf;

    SET @sfm_start = LAST_INSERT_ID();

    INSERT INTO short_form (media_id, series_id, contents_id, duration, video_size, origin_url, master_playlist_url, created_date, modified_date, status)
    SELECT
        @sfm_start + rn - 1,
        NULL,
        IF(RAND(rn + 2600000) < 0.5, @c_start + FLOOR(RAND(rn + 2700000) * @c_count), NULL),
        FLOOR(15 + RAND(rn + 2800000) * 45),
        FLOOR(10 + RAND(rn + 2900000) * 100),
        CONCAT('/videos/short_', rn, '.mp4'),
        IF(RAND(rn + 2400000) < 0.9, CONCAT('/hls/short_', rn, '/master.m3u8'), NULL),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 2500000) * 365) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_sf/10000.0)) nums
    WHERE rn <= @v_sf;

    SET @sf_start = LAST_INSERT_ID();
    SET @sf_count = @v_sf;

    -- Track overall media range
    SET @media_start = @sm_start;
    SET @media_count = @v_media;
    SELECT CONCAT('[6/16] media(SHORT_FORM) + short_form: ', @v_sf) AS progress;

    -- =============================================
    -- 7. MEDIA_TAG (3 per media, offsets 0/9/19 coprime to 28)
    -- =============================================
    INSERT INTO media_tag (tag_id, media_id, created_date, modified_date, status)
    SELECT
        ((nums.rn - 1 + t.offset) % 28) + 1,
        @media_start + nums.rn - 1,
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_media/10000.0)) nums
    CROSS JOIN (SELECT 0 AS offset UNION ALL SELECT 9 UNION ALL SELECT 19) t
    WHERE nums.rn <= @v_media;

    SELECT CONCAT('[7/16] media_tag: ', @v_media * 3) AS progress;

    -- =============================================
    -- 8. MEDIA_MOOD_TAG (2 per media, offsets 0/15 coprime to 31, INSERT IGNORE for UK)
    -- =============================================
    INSERT IGNORE INTO media_mood_tag (media_id, mood_tag_id, priority, created_date, modified_date, status)
    SELECT
        @media_start + nums.rn - 1,
        ((nums.rn - 1 + t.offset) % 31) + 1,
        t.pri,
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_media/10000.0)) nums
    CROSS JOIN (SELECT 0 AS offset, 0 AS pri UNION ALL SELECT 15, 1) t
    WHERE nums.rn <= @v_media;

    SELECT CONCAT('[8/16] media_mood_tag: ~', @v_media * 2) AS progress;

    -- =============================================
    -- 9. MEDIA_METRICS (1:1 with media)
    -- =============================================
    INSERT INTO media_metrics (media_id, popularity, immersion, mania, recency, re_watch, batch_updated_at, created_date, modified_date, status)
    SELECT
        @media_start + rn - 1,
        ROUND(RAND(rn) * 100, 2),
        ROUND(RAND(rn + 100000) * 100, 2),
        ROUND(RAND(rn + 200000) * 100, 2),
        ROUND(RAND(rn + 300000) * 100, 2),
        ROUND(RAND(rn + 400000) * 100, 2),
        NOW(),
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_media/10000.0)) nums
    WHERE rn <= @v_media;

    SELECT CONCAT('[9/16] media_metrics: ', @v_media) AS progress;

    -- Re-enable unique checks for INSERT IGNORE sections
    SET UNIQUE_CHECKS = 1;

    -- =============================================
    -- 10. BOOKMARK (Chunked bulk INSERT IGNORE, 1M per chunk)
    -- =============================================
    SET @a_max = CEIL(@v_bookmark / 10000.0);
    SET @a_offset = 0;
    WHILE @a_offset < @a_max DO
        INSERT IGNORE INTO bookmark (member_id, media_id, created_date, modified_date, status)
        SELECT
            @m_start + FLOOR(RAND(rn + 3000000) * @v_member),
            @media_start + FLOOR(POW(RAND(rn + 3100000), 2) * @media_count),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 3200000) * 180) DAY),
            NOW(), 'ACTIVE'
        FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b
              WHERE a.n > @a_offset AND a.n <= @a_offset + 100) nums
        WHERE rn <= @v_bookmark;
        SET @a_offset = @a_offset + 100;
    END WHILE;

    SELECT CONCAT('[10/16] bookmark: ', (SELECT COUNT(*) FROM bookmark)) AS progress;

    -- =============================================
    -- 11. LIKES (Chunked bulk INSERT IGNORE, 1M per chunk)
    -- =============================================
    SET @a_max = CEIL(@v_likes / 10000.0);
    SET @a_offset = 0;
    WHILE @a_offset < @a_max DO
        INSERT IGNORE INTO likes (member_id, media_id, created_date, modified_date, status)
        SELECT
            @m_start + FLOOR(RAND(rn + 4000000) * @v_member),
            @media_start + FLOOR(POW(RAND(rn + 4100000), 2) * @media_count),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 4200000) * 180) DAY),
            NOW(), 'ACTIVE'
        FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b
              WHERE a.n > @a_offset AND a.n <= @a_offset + 100) nums
        WHERE rn <= @v_likes;
        SET @a_offset = @a_offset + 100;
    END WHILE;

    SELECT CONCAT('[11/16] likes: ', (SELECT COUNT(*) FROM likes)) AS progress;

    -- =============================================
    -- 12. COMMENT (contents only)
    -- =============================================
    INSERT INTO comment (member_id, contents_id, content, is_spoiler, created_date, modified_date, status)
    SELECT
        @m_start + FLOOR(RAND(rn + 6000000) * @v_member),
        @c_start + FLOOR(RAND(rn + 7000000) * @c_count),
        CONCAT('테스트 댓글 ', rn),
        IF(RAND(rn + 8000000) < 0.1, TRUE, FALSE),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 9000000) * 180) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_comment/10000.0)) nums
    WHERE rn <= @v_comment;

    SELECT CONCAT('[12/16] comment: ', @v_comment) AS progress;

    -- =============================================
    -- 13. WATCH_HISTORY (Chunked bulk INSERT IGNORE, 1M per chunk)
    -- =============================================
    SET @a_max = CEIL(@v_watch / 10000.0);
    SET @a_offset = 0;
    WHILE @a_offset < @a_max DO
        INSERT IGNORE INTO watch_history (member_id, contents_id, last_watched_at, re_watch_count, is_used_for_ml, created_date, modified_date, status)
        SELECT
            @m_start + FLOOR(RAND(rn + 10000000) * @v_member),
            @c_start + FLOOR(RAND(rn + 10100000) * @c_count),
            DATE_SUB(NOW(), INTERVAL FLOOR(POW(RAND(rn + 10200000), 2) * 90) DAY),
            CASE WHEN RAND(rn + 10300000) < 0.80 THEN 0 WHEN RAND(rn + 10400000) < 0.95 THEN 1 ELSE FLOOR(2 + RAND(rn + 10500000) * 3) END,
            FALSE,
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 10600000) * 365) DAY),
            NOW(), 'ACTIVE'
        FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b
              WHERE a.n > @a_offset AND a.n <= @a_offset + 100) nums
        WHERE rn <= @v_watch;
        SET @a_offset = @a_offset + 100;
    END WHILE;

    SELECT CONCAT('[13/16] watch_history: ', (SELECT COUNT(*) FROM watch_history)) AS progress;

    -- =============================================
    -- 14. PLAYBACK (Chunked bulk INSERT IGNORE, 1M per chunk)
    -- =============================================
    SET @a_max = CEIL(@v_playback / 10000.0);
    SET @a_offset = 0;
    WHILE @a_offset < @a_max DO
        INSERT IGNORE INTO playback (member_id, contents_id, position_sec, created_date, modified_date, status)
        SELECT
            @m_start + FLOOR(RAND(rn + 11000000) * @v_member),
            @c_start + FLOOR(RAND(rn + 11100000) * @c_count),
            FLOOR(RAND(rn + 11200000) * 7200),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 11300000) * 90) DAY),
            NOW(), 'ACTIVE'
        FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b
              WHERE a.n > @a_offset AND a.n <= @a_offset + 100) nums
        WHERE rn <= @v_playback;
        SET @a_offset = @a_offset + 100;
    END WHILE;

    SELECT CONCAT('[14/16] playback: ', (SELECT COUNT(*) FROM playback)) AS progress;

    -- =============================================
    -- 15. CLICK_EVENT
    -- =============================================
    INSERT INTO click_event (member_id, short_form_id, click_at, click_type, created_date, modified_date, status)
    SELECT
        @m_start + FLOOR(RAND(rn + 20000000) * @v_member),
        @sf_start + FLOOR(RAND(rn + 21000000) * @sf_count),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 22000000) * 90) DAY),
        IF(RAND(rn + 23000000) < 0.7, 'SHORT_CLICK', 'CTA_CLICK'),
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND(rn + 22000000) * 90) DAY),
        NOW(),
        'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_click/10000.0)) nums
    WHERE rn <= @v_click;

    SELECT CONCAT('[15/16] click_event: ', @v_click) AS progress;

    -- =============================================
    -- 16. MEMBER_MOOD_REFRESH (50% of members)
    -- =============================================
    SET @v_mood_refresh = FLOOR(@v_member * 0.5);

    INSERT INTO member_mood_refresh (member_id, image_id, subtitle, recommended_media_ids, is_hidden, created_date, modified_date, status)
    SELECT
        @m_start + rn - 1,
        FLOOR(RAND(rn + 24000000) * 10) + 1,
        CONCAT('오늘의 무드 ', rn),
        JSON_ARRAY(
            @media_start + FLOOR(RAND(rn + 25000000) * @media_count),
            @media_start + FLOOR(RAND(rn + 25100000) * @media_count),
            @media_start + FLOOR(RAND(rn + 25200000) * @media_count),
            @media_start + FLOOR(RAND(rn + 25300000) * @media_count),
            @media_start + FLOOR(RAND(rn + 25400000) * @media_count)
        ),
        IF(RAND(rn + 26000000) < 0.2, TRUE, FALSE),
        NOW(), NOW(), 'ACTIVE'
    FROM (SELECT ((a.n-1)*10000+b.n) rn FROM _seed_seq a CROSS JOIN _seed_seq b WHERE a.n <= CEIL(@v_mood_refresh/10000.0)) nums
    WHERE rn <= @v_mood_refresh;

    SELECT CONCAT('[16/16] member_mood_refresh: ', @v_mood_refresh) AS progress;

    -- =============================================
    -- 17. SYNC DE-NORMALIZED COUNTS
    -- =============================================
    UPDATE media m
    LEFT JOIN (SELECT media_id, COUNT(*) cnt FROM likes WHERE status = 'ACTIVE' GROUP BY media_id) lc ON m.id = lc.media_id
    LEFT JOIN (SELECT media_id, COUNT(*) cnt FROM bookmark WHERE status = 'ACTIVE' GROUP BY media_id) bc ON m.id = bc.media_id
    SET m.likes_count = COALESCE(lc.cnt, 0),
        m.bookmark_count = COALESCE(bc.cnt, 0);

    SELECT '[17/17] Sync de-normalized counts: media(likes_count, bookmark_count) updated' AS progress;

    -- =============================================
    -- CLEANUP
    -- =============================================
    SET FOREIGN_KEY_CHECKS = 1;
    SET UNIQUE_CHECKS = 1;
    DROP TABLE IF EXISTS _seed_seq;

    SELECT CONCAT('Seed completed! preset=', p_preset,
        ', elapsed=', TIMESTAMPDIFF(SECOND, @start_time, NOW()), 's',
        ', members=', @v_member, ', media=', @v_media,
        ', bookmarks=', @v_bookmark, ', watch_history=', @v_watch) AS result;

END //

DELIMITER ;

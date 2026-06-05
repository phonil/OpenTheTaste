-- =============================================
-- OTT 서비스 카테고리/태그 고정 메타데이터
-- 사용법: mysql -u ott -p ott < scripts/seed-metadata.sql
-- =============================================

INSERT INTO category (id, name, created_date, modified_date, status) VALUES
(1, '장르',     NOW(), NOW(), 'ACTIVE'),
(2, '분위기',   NOW(), NOW(), 'ACTIVE'),
(3, '시청상황', NOW(), NOW(), 'ACTIVE'),
(4, '테마',     NOW(), NOW(), 'ACTIVE')
ON DUPLICATE KEY UPDATE name = VALUES(name), modified_date = NOW();

INSERT INTO tag (id, category_id, name, created_date, modified_date, status) VALUES
-- 장르 (category_id = 1)
(1,  1, '액션',       NOW(), NOW(), 'ACTIVE'),
(2,  1, '로맨스',     NOW(), NOW(), 'ACTIVE'),
(3,  1, 'SF',         NOW(), NOW(), 'ACTIVE'),
(4,  1, '스릴러',     NOW(), NOW(), 'ACTIVE'),
(5,  1, '코미디',     NOW(), NOW(), 'ACTIVE'),
(6,  1, '드라마',     NOW(), NOW(), 'ACTIVE'),
(7,  1, '호러',       NOW(), NOW(), 'ACTIVE'),
(8,  1, '판타지',     NOW(), NOW(), 'ACTIVE'),
(9,  1, '다큐멘터리', NOW(), NOW(), 'ACTIVE'),
(10, 1, '애니메이션', NOW(), NOW(), 'ACTIVE'),
-- 분위기 (category_id = 2)
(11, 2, '긴장감',   NOW(), NOW(), 'ACTIVE'),
(12, 2, '따뜻한',   NOW(), NOW(), 'ACTIVE'),
(13, 2, '유쾌한',   NOW(), NOW(), 'ACTIVE'),
(14, 2, '어두운',   NOW(), NOW(), 'ACTIVE'),
(15, 2, '감동적인', NOW(), NOW(), 'ACTIVE'),
-- 시청상황 (category_id = 3)
(16, 3, '혼자볼때', NOW(), NOW(), 'ACTIVE'),
(17, 3, '연인과',   NOW(), NOW(), 'ACTIVE'),
(18, 3, '가족과',   NOW(), NOW(), 'ACTIVE'),
(19, 3, '심심할때', NOW(), NOW(), 'ACTIVE'),
(20, 3, '잠들기전', NOW(), NOW(), 'ACTIVE'),
-- 테마 (category_id = 4)
(21, 4, '성장', NOW(), NOW(), 'ACTIVE'),
(22, 4, '복수', NOW(), NOW(), 'ACTIVE'),
(23, 4, '우정', NOW(), NOW(), 'ACTIVE'),
(24, 4, '가족', NOW(), NOW(), 'ACTIVE'),
(25, 4, '사랑', NOW(), NOW(), 'ACTIVE'),
(26, 4, '생존', NOW(), NOW(), 'ACTIVE'),
(27, 4, '범죄', NOW(), NOW(), 'ACTIVE'),
(28, 4, '일상', NOW(), NOW(), 'ACTIVE')
ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), name = VALUES(name), modified_date = NOW();

SELECT CONCAT('Metadata seeded: ', (SELECT COUNT(*) FROM category), ' categories, ', (SELECT COUNT(*) FROM tag), ' tags') AS result;

# 테스트 데이터 시드 스크립트

## 환경

- Windows 11 + Docker Desktop
- MySQL 8.0 (Docker 컨테이너: `ott-mysql`, 포트 3307)
- DB: `ott`, User: `ott`, Password: `ottpw`
- 터미널: **Git Bash** (VS Code 터미널, Claude Code 등)

> 로컬에 mysql 클라이언트가 없으므로 `docker exec`를 통해 컨테이너 내부의 mysql을 사용합니다.

## 사전 조건

1. Docker Desktop 실행 중
2. `ott-mysql` 컨테이너 실행 중 (`docker ps`로 확인)
3. Flyway 마이그레이션(V1~V13) 적용 완료 (api-user 한 번이라도 실행했으면 OK)

컨테이너가 안 떠있으면:
```bash
cd "C:/Users/kkwas/OneDrive/desktop/유레카/최종 융합 프로젝트/backend"
docker-compose up -d mysql
```

## 사용법 (Git Bash 기준)

모든 명령은 **프로젝트 루트**에서 실행합니다:
```bash
cd "C:/Users/kkwas/OneDrive/desktop/유레카/최종 융합 프로젝트/backend"
```

### Step 1. 메타데이터 삽입 (최초 1회)

category 4개 + tag 28개를 생성합니다. 이미 있으면 건너뜁니다.

```bash
docker exec -i ott-mysql mysql -u ott -pottpw ott < scripts/seed-metadata.sql
```

### Step 2. 프로시저 등록

`seed_all` 프로시저를 DB에 등록합니다. 스크립트 수정 시마다 다시 실행하면 됩니다.

```bash
docker exec -i ott-mysql mysql -u ott -pottpw ott < scripts/seed-procedures.sql
```

### Step 3. 시드 데이터 생성

원하는 프리셋을 선택해서 실행합니다.

```bash
# small (~10초) - 빠른 확인용
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('small');"

# medium (~1분) - 기본 성능 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('medium');"

# large (~10분) - 부하 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('large');"

# xl (~1시간+) - 대규모 스트레스 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('xl');"
```

> **주의**: 시드 실행 전에 기존 테스트 데이터가 없어야 합니다. 이미 있으면 Step 5로 먼저 삭제하세요.

### Step 4. 데이터 확인

테이블별 행 수를 확인합니다.

```bash
docker exec ott-mysql mysql -u ott -pottpw ott -e "
SELECT 'member' AS tbl, COUNT(*) AS cnt FROM member
UNION ALL SELECT 'media', COUNT(*) FROM media
UNION ALL SELECT 'series', COUNT(*) FROM series
UNION ALL SELECT 'contents', COUNT(*) FROM contents
UNION ALL SELECT 'short_form', COUNT(*) FROM short_form
UNION ALL SELECT 'bookmark', COUNT(*) FROM bookmark
UNION ALL SELECT 'likes', COUNT(*) FROM likes
UNION ALL SELECT 'comment', COUNT(*) FROM comment
UNION ALL SELECT 'watch_history', COUNT(*) FROM watch_history
UNION ALL SELECT 'playback', COUNT(*) FROM playback
UNION ALL SELECT 'click_event', COUNT(*) FROM click_event
UNION ALL SELECT 'media_tag', COUNT(*) FROM media_tag
UNION ALL SELECT 'media_mood_tag', COUNT(*) FROM media_mood_tag
UNION ALL SELECT 'media_metrics', COUNT(*) FROM media_metrics;"
```

### Step 5. 데이터 삭제

모든 테스트 데이터를 삭제합니다. 메타데이터(category, tag, mood_category, mood_tag)는 유지됩니다.

```bash
docker exec -i ott-mysql mysql -u ott -pottpw ott < scripts/clean-data.sql
```

### 프리셋 교체 (삭제 후 재생성)

```bash
docker exec -i ott-mysql mysql -u ott -pottpw ott < scripts/clean-data.sql
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('large');"
```

## 프리셋 규모

| 테이블 | small | medium | large | xl |
|--------|-------|--------|-------|-----|
| member | 100 | 1,000 | 10,000 | 10,000 |
| media (total) | 1,000 | 10,000 | 50,000 | 500,000 |
| ┗ series | 100 | 1,000 | 5,000 | 50,000 |
| ┗ contents | 600 | 6,000 | 30,000 | 300,000 |
| ┗ short_form | 300 | 3,000 | 15,000 | 150,000 |
| bookmark | 5,000 | 50,000 | 200,000 | 2,000,000 |
| likes | 5,000 | 50,000 | 200,000 | 2,000,000 |
| comment | 3,000 | 30,000 | 100,000 | 1,000,000 |
| watch_history | 10,000 | 100,000 | 500,000 | 5,000,000 |
| playback | 5,000 | 50,000 | 200,000 | 2,000,000 |
| click_event | 3,000 | 30,000 | 100,000 | 1,000,000 |

## PowerShell에서 실행하는 경우 (권장)

PowerShell의 파이프라인(`|`)은 한글 인코딩을 변환하여 데이터가 `???`로 깨질 수 있습니다. **`cmd /c`와 `<` 리다이렉션**을 사용하는 것이 가장 안전합니다.

```powershell
# 메타데이터 삽입
cmd /c "docker exec -i ott-mysql mysql -u ott -pottpw --default-character-set=utf8mb4 ott < scripts/seed-metadata.sql"

---

# 프로시저 등록
cmd /c "docker exec -i ott-mysql mysql -u ott -pottpw --default-character-set=utf8mb4 ott < scripts/seed-procedures.sql"

---

# 시드 데이터 생성
## small (~10초) - 빠른 확인용
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('small');"

## medium (~1분) - 기본 성능 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('medium');"

## large (~10분) - 부하 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('large');"

## xl (~1시간+) - 대규모 스트레스 테스트
docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('xl');"

---

# 데이터 삭제
cmd /c "docker exec -i ott-mysql mysql -u ott -pottpw ott < scripts/clean-data.sql"
```

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `ERROR: Unknown preset` | 프리셋 오타 | small, medium, large, xl 중 하나 |
| `ERROR: Run seed-metadata.sql first!` | category/tag 없음 | Step 1 먼저 실행 |
| `Duplicate entry` | 이미 데이터 있음 | `clean-data.sql`로 삭제 후 재실행 |
| 한글이 `???`로 나옴 | PowerShell 인코딩 간섭 | 위 안내된 `cmd /c` 방식 사용 |
| xl이 너무 느림 | 대량 데이터 | innodb_buffer_pool_size 늘리기 |
| `the input device is not a TTY` | `-i` 없이 파일 입력 | `docker exec -i`로 실행 |

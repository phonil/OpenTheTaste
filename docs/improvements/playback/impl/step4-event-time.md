# Step 4: event_time 정합성 (순서 역전 방지)

## 문제

큐 기반 write-behind에서 flush 순서가 요청 순서와 달라질 수 있다.

```
요청 순서:  positionSec=100 (13:00:01) → positionSec=200 (13:00:06)
flush 순서: positionSec=200 먼저 flush → positionSec=100 나중에 flush
→ DB에 100이 덮어씌워짐 (사용자는 200초까지 봤는데 100초로 저장)
```

단일 서버 + 단일 Worker라면 큐의 FIFO가 순서를 보장하지만, 다음 상황에서 역전 가능:
- 다중 서버 (서버 A의 flush와 서버 B의 flush가 동시에)
- retryBuffer에서 재시도 시 순서 뒤바뀜
- 향후 Worker 다중화 시

## 해결

`event_time` 컬럼을 추가하고, UPDATE 시 기존 event_time보다 클 때만 갱신.

```sql
UPDATE playback
SET position_sec = ?, modified_date = NOW(), event_time = ?
WHERE member_id = ? AND contents_id = ? AND status = 'ACTIVE'
  AND event_time < ?
```

event_time이 더 오래된 요청이 나중에 도착해도 무시됨.

## 설계 판단: NULL 체크 제거

### 선택지

| 방안 | SQL 조건 | 단점 |
|------|----------|------|
| event_time NULL 허용 | `AND (event_time IS NULL OR event_time < ?)` | 조건 복잡, NULL 예외 케이스 |
| event_time NOT NULL + 기본값 | `AND event_time < ?` | 단순 |

### 결정

`initPlayback()`에서 INSERT 시 `event_time = NOW()`를 넣고, Flyway에서 기존 row에도 `DEFAULT NOW()`로 채움.
→ event_time이 항상 존재 → `IS NULL` 체크 불필요 → SQL 단순화.

## 변경 파일

### 신규

| 파일 | 내용 |
|------|------|
| `V16__add_event_time_to_playback.sql` | `ALTER TABLE playback ADD COLUMN event_time DATETIME(3) NOT NULL DEFAULT NOW()` |

### 수정

| 파일 | 변경 |
|------|------|
| `Playback.java` | `eventTime` 필드 추가 |
| `PlaybackRepository.java` | `insertIgnorePlayback()`에 event_time 추가 |
| `PlaybackBatchRepository.java` | UPDATE SQL + 파라미터 바인딩 변경 |

## 변경 상세

### V16 마이그레이션

```sql
ALTER TABLE playback ADD COLUMN event_time DATETIME(3) NOT NULL DEFAULT NOW();
```

`DATETIME(3)` — 밀리초 정밀도. `PlaybackCommand.requestedAt`이 `Instant`(나노초)이므로 밀리초까지 저장.

기존 row는 마이그레이션 시점의 NOW()로 채워짐.

### Playback 엔티티

```java
// Before
@Column(name = "position_sec", nullable = false)
private Integer positionSec;

// After
@Column(name = "position_sec", nullable = false)
private Integer positionSec;

@Column(name = "event_time", nullable = false)
private LocalDateTime eventTime;
```

JPA `ddl-auto: validate` 통과용.

### PlaybackRepository — insertIgnorePlayback

```sql
-- Before
INSERT IGNORE INTO playback (member_id, contents_id, position_sec, created_date, modified_date, status)
VALUES (:memberId, :contentsId, 0, NOW(), NOW(), 'ACTIVE')

-- After
INSERT IGNORE INTO playback (member_id, contents_id, position_sec, event_time, created_date, modified_date, status)
VALUES (:memberId, :contentsId, 0, NOW(), NOW(), NOW(), 'ACTIVE')
```

INSERT 시점에 event_time을 채워서 NULL이 없도록 보장.

### PlaybackBatchRepository — UPDATE SQL + 바인딩

```sql
-- Before
UPDATE playback
SET position_sec = ?,
    modified_date = NOW()
WHERE member_id = ?
  AND contents_id = ?
  AND status = 'ACTIVE'

-- After
UPDATE playback
SET position_sec = ?,
    modified_date = NOW(),
    event_time = ?
WHERE member_id = ?
  AND contents_id = ?
  AND status = 'ACTIVE'
  AND event_time < ?
```

```java
// Before
(ps, cmd) -> {
    ps.setInt(1, cmd.positionSec());
    ps.setLong(2, cmd.memberId());
    ps.setLong(3, cmd.contentsId());
}

// After
(ps, cmd) -> {
    Timestamp eventTime = Timestamp.from(cmd.requestedAt());
    ps.setInt(1, cmd.positionSec());
    ps.setTimestamp(2, eventTime);
    ps.setLong(3, cmd.memberId());
    ps.setLong(4, cmd.contentsId());
    ps.setTimestamp(5, eventTime);
}
```

`PlaybackCommand.requestedAt()`은 API 요청 시점에 `Instant.now()`로 찍힌 값. 이걸 event_time으로 SET하고, WHERE 조건의 비교값으로도 동일하게 사용.

## 동작 시나리오

| 상황 | DB event_time | 요청 event_time | 결과 |
|------|---------------|-----------------|------|
| 정상 순서 | 13:00:01 | 13:00:06 | 갱신 O (06 > 01) |
| 역전 도착 | 13:00:06 | 13:00:01 | 갱신 X (01 < 06) → 무시 |
| 동일 시각 | 13:00:01 | 13:00:01 | 갱신 X (01 < 01 = false) → 멱등 |

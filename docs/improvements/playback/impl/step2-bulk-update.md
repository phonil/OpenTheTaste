# Step 2: Bulk Update (batch 처리)

## 목적

FlushWorker의 단건 UPDATE loop(commit N번)을 `jdbcTemplate.batchUpdate()`(commit 1번)로 교체한다.

## Before / After

```
[Before — Step 1]
FlushWorker → for (cmd : drained) { playbackRepository.updatePlayback() }
              ↑ UPDATE N번, commit N번 (autoCommit)

[After — Step 2]
FlushWorker → batchRepository.batchUpdate(drained)
              ↑ UPDATE N번, commit 1번 (@Transactional)
```

## 생성/수정 파일

### 신규 파일

| 파일 | 역할 |
|------|------|
| `PlaybackBatchRepository.java` | `jdbcTemplate.batchUpdate()` + `@Transactional` + key sorting. buffer 전용 batch 처리기 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `PlaybackFlushWorker.java` | `PlaybackRepository` 의존 제거 → `PlaybackBatchRepository` 교체. 단건 loop → `batchUpdate()` 한 줄 |

## 핵심 구현 상세

### PlaybackBatchRepository

```java
@Repository
@RequiredArgsConstructor
public class PlaybackBatchRepository {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchUpdate(Collection<PlaybackCommand> commands) {
        List<PlaybackCommand> sorted = commands.stream()
            .sorted(Comparator.comparing(PlaybackCommand::memberId)
                               .thenComparing(PlaybackCommand::contentsId))
            .toList();

        jdbcTemplate.batchUpdate(UPDATE_SQL, sorted, sorted.size(),
            (ps, cmd) -> {
                ps.setInt(1, cmd.positionSec());
                ps.setLong(2, cmd.memberId());
                ps.setLong(3, cmd.contentsId());
            });
    }
}
```

#### `@Transactional`을 Repository에 둔 이유

일반적으로 `@Transactional`은 Service 계층에 두는 것이 원칙이다. Repository에 두는 것은 안티패턴이다.

하지만 현재 구조에서는 다른 선택지가 제한적이다:

| 방안 | 문제 |
|------|------|
| FlushWorker에 `@Transactional` | Worker 내부에서 `this.flush()` 호출 = self-invocation → **프록시를 안 탐** → `@Transactional` 안 먹음 |
| 별도 Service 분리 | FlushWorker → FlushService → Repository. 클래스 하나 더 만드는 과설계 |
| `TransactionTemplate` | 작동하지만, 어차피 단일 SQL로 전환하면 `@Transactional` 자체가 불필요해짐 |

**현재 결정:** 단일 SQL(CASE WHEN)로 전환하면 `@Transactional`이 필요 없어지므로, 지금은 Repository에 두고 전환 시 같이 제거한다. 상세 분석은 `problem/batchUpdate-vs-single-sql.md` 참조.

#### key sorting — 데드락 구조적 제거

```java
List<PlaybackCommand> sorted = commands.stream()
    .sorted(Comparator.comparing(PlaybackCommand::memberId)
                       .thenComparing(PlaybackCommand::contentsId))
    .toList();
```

**문제:** 다중 서버/스레드에서 같은 row들을 다른 순서로 UPDATE하면 데드락 발생 가능.

```
Thread A: UPDATE member=1,contents=10 → UPDATE member=2,contents=20  (lock 1→2)
Thread B: UPDATE member=2,contents=20 → UPDATE member=1,contents=10  (lock 2→1)
→ 교차 대기 → 데드락
```

**해결:** `(memberId, contentsId)` 오름차순 정렬 → 모든 스레드가 같은 순서로 lock 획득 → 교차 대기 불가능.

#### `jdbcTemplate.batchUpdate()`의 동작

```java
jdbcTemplate.batchUpdate(UPDATE_SQL, sorted, sorted.size(), (ps, cmd) -> { ... });
```

- **SQL 실행**: N개의 개별 UPDATE문을 DB에 전송
- **commit**: `@Transactional`로 묶여서 1번만 commit
- **네트워크**: JDBC batch protocol로 여러 문장을 한 번에 전송 (드라이버 의존)

**알려진 한계:** SQL 자체는 N개 실행된다. 쿼리 수를 줄이려면 단일 SQL(CASE WHEN)로 전환해야 한다. `problem/batchUpdate-vs-single-sql.md`에 상세 분석 및 전환 계획이 있다.

#### SQL이 기존과 동일

```sql
UPDATE playback
SET position_sec = ?,
    modified_date = NOW()
WHERE member_id = ?
  AND contents_id = ?
  AND status = 'ACTIVE'
```

`PlaybackRepository.updatePlayback()`과 같은 SQL. 동작이 달라지는 것 없이 실행 방식만 batch로 변경.

#### 참조한 기존 패턴

`MediaMetricsJdbcRepository.bulkUpsert()`:

```java
jdbcTemplate.batchUpdate(sql, rowList, rowList.size(),
    (ps, row) -> {
        ps.setLong(1, row.mediaId());
        // ...
    });
```

프로젝트 내 기존 `jdbcTemplate.batchUpdate()` 사용 패턴을 동일하게 따름.

### PlaybackFlushWorker 변경

```java
// Before (Step 1)
private final PlaybackRepository playbackRepository;

private void flush() throws InterruptedException {
    List<PlaybackCommand> drained = commandQueue.drain(bulkSize, flushTimeoutMs);
    if (drained.isEmpty()) return;

    for (PlaybackCommand cmd : drained) {
        try {
            playbackRepository.updatePlayback(
                cmd.memberId(), cmd.contentsId(), cmd.positionSec());
        } catch (Exception e) {
            log.error("Single update failed: key=({}, {})",
                cmd.memberId(), cmd.contentsId(), e);
        }
    }
}

// After (Step 2)
private final PlaybackBatchRepository batchRepository;

private void flush() throws InterruptedException {
    List<PlaybackCommand> drained = commandQueue.drain(bulkSize, flushTimeoutMs);
    if (drained.isEmpty()) return;

    batchRepository.batchUpdate(drained);
}
```

**변경 포인트:**
- `PlaybackRepository` 의존 제거 → `PlaybackBatchRepository`로 교체
- 개별 try-catch loop 삭제 → `batchUpdate()` 한 줄
- 실패 시 전체 batch rollback (`@Transactional`). Step 5에서 retryBuffer 추가 예정

**개별 try-catch를 제거한 이유:**
- Step 1에서는 단건 처리라 한 건 실패해도 나머지를 처리할 수 있었음
- Step 2에서는 하나의 트랜잭션이므로, 부분 성공이 불가능
- 실패 시 `leaderLoop`의 `catch (Exception e) → log.error`에서 잡히고 다음 flush를 계속 시도

## 테스트

### PlaybackBatchRepositoryTest (신규, 통합 테스트)

| 테스트 | 검증 |
|--------|------|
| `batchUpdate_updatesMultipleRows` | 3개 command → 3개 row의 position_sec 갱신 확인 |
| `batchUpdate_ignoresNonExistentRow` | 존재하지 않는 (memberId, contentsId) → 에러 없이 무시 |
| `batchUpdate_emptyCollection` | 빈 컬렉션 → 에러 없이 완료 |

`@JdbcTest` + `@Import(PlaybackBatchRepository.class)` + `@Sql`로 H2 인메모리 DB에서 실행.

## 이후 변경사항

### 이중 트리거 drain (구현 완료)

`PlaybackCommandQueue.drain()`을 이중 트리거 방식으로 변경:
- **bulkSize 트리거**: 큐에 1000건 이상 → 즉시 반환
- **timeout 트리거**: 5초 동안 모아서 반환

설정값 확정:

| 설정 | 값 | 근거 |
|------|-----|------|
| queue-capacity | 10,000 | WPS 378 기준 26초 버퍼. 장애 시 유실 범위와 overflow 빈도의 타협 |
| bulk-size | 1,000 | vu2000(WPS 378)에서 ~2.6초에 트리거. 저부하에서는 timeout이 커버 |
| flush-timeout-ms | 5,000 | 이어보기 특성상 5초 지연 허용. 저부하 배치 효과 확보 |

### Coalescing 불필요 (제외)

클라이언트가 5초 주기로 전송, flush는 최대 5초 주기 → 같은 key가 큐에 중복 적재될 가능성 없음. 상세: `problem/coalescing-necessity.md`

## 남은 한계 (다음 Step에서 해결)

| 한계 | 해결 Step |
|------|-----------|
| SQL N개 실행 (commit만 1번) | 단일 SQL 전환 검토 (`problem/batchUpdate-vs-single-sql.md`) |
| event_time 정합성 없음 | Step 4: 조건부 UPDATE |
| 실패 시 전체 rollback, 재시도 없음 | Step 5: retryBuffer |

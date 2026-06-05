# batchUpdate() vs 단일 SQL UPDATE 문제

## 문제 인식

Playback 위치 갱신의 원래 목적은 **쿼리 수를 줄이는 것**이다.
그런데 `jdbcTemplate.batchUpdate()`는 SQL을 N개 실행하고 1 트랜잭션으로 묶을 뿐, 쿼리 수 자체는 줄지 않는다.

```
[현재 설계: batchUpdate()]
UPDATE playback SET position_sec = 120 WHERE member_id = 1 AND contents_id = 10 AND status = 'ACTIVE';
UPDATE playback SET position_sec = 350 WHERE member_id = 2 AND contents_id = 20 AND status = 'ACTIVE';
UPDATE playback SET position_sec = 500 WHERE member_id = 3 AND contents_id = 30 AND status = 'ACTIVE';
→ SQL 3개, commit 1번
```

commit 횟수는 줄지만, DB가 실행하는 SQL 수는 N개 그대로다.

## 대안: CASE WHEN 단일 SQL

```sql
UPDATE playback
SET position_sec = CASE
      WHEN member_id = 1 AND contents_id = 10 THEN 120
      WHEN member_id = 2 AND contents_id = 20 THEN 350
      WHEN member_id = 3 AND contents_id = 30 THEN 500
    END,
    modified_date = NOW()
WHERE (member_id, contents_id) IN ((1,10), (2,20), (3,30))
  AND status = 'ACTIVE'
```

- SQL 1개로 N개 row를 한 번에 UPDATE
- commit도 1번
- `@Transactional` 불필요 (SQL 1개 = autoCommit으로 충분)

## rewriteBatchedStatements=true는?

MySQL JDBC 드라이버 옵션. `batchUpdate()`와 조합 시:

| 구문 | 효과 |
|------|------|
| INSERT | 개별 INSERT를 `INSERT INTO ... VALUES (...), (...), (...)` 단일 SQL로 재작성 → 효과 있음 |
| UPDATE | 단일 SQL로 재작성 안 됨. 여러 UPDATE를 하나의 네트워크 패킷에 묶어 보낼 뿐, DB에선 여전히 N개 실행 |

**결론: UPDATE에는 `rewriteBatchedStatements`로 해결되지 않는다.**

## 참고: logbat의 접근

[logbat (woowa-techcamp-2024)](https://github.com/woowa-techcamp-2024/Team5-Guys)은 동적으로 단일 INSERT SQL을 생성하는 방식을 사용한다.

```java
// logbat - AsyncLogRepository.java
StringBuilder sql = new StringBuilder(
    "INSERT INTO logs (app_id, level, data, timestamp) VALUES ");
for (int i = 0; i < logs.size(); i++) {
    sql.append("(?, ?, ?, ?)");
    if (i < logs.size() - 1) sql.append(", ");
}
jdbcTemplate.update(sql.toString(), ps -> { ... });
```

- `@Transactional` 없음 — SQL 1개이므로 불필요
- `batchUpdate()` 안 씀 — `jdbcTemplate.update()` 사용
- INSERT이므로 multi-row VALUES로 자연스럽게 묶임

## 현재 결정

Step 2는 우선 `batchUpdate()` 방식으로 구현하고, k6 부하 테스트(Step 6)에서 성능을 측정한 뒤 CASE WHEN 단일 SQL 방식으로 전환 여부를 판단한다.

전환 시 변경 범위:
- `PlaybackBatchRepository.batchUpdate()` 내부 SQL + 호출 방식만 변경
- FlushWorker, Queue 등 나머지 구조는 영향 없음

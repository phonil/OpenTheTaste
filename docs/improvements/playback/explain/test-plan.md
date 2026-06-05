# Playback Buffering 테스트 계획

## 테스트 시점 판단

모든 Step마다 부하 테스트를 하지 않는다. 의미 있는 변화가 생긴 시점에만 한다.

| Step | 부하 테스트 | 이유 |
|------|------------|------|
| Step 1+2 | **O** | Before/After의 핵심 비교 지점. 직접 DB → queue+bulk로 바뀐 첫 시점 |
| Step 3 | **O** | Coalescing 효과를 수치로 확인해야 함. 중복 제거율에 따라 단일 SQL 전환 판단 |
| Step 4 | **X** | event_time은 정합성 문제. 단위/통합 테스트로 충분 |
| Step 5 | **X** | 이중 트리거, retryBuffer는 안정성 개선. Step 5 완료 후 최종 부하 테스트에 포함 |
| 최종 | **O** | 모든 최적화 적용 후 T3~T4 단계까지 스트레스 테스트 |

---

## 부하 단계 정의

| 단계 | 동시 시청자 | req/s | k6 옵션 | 용도 |
|------|------------|-------|---------|------|
| T1 | 100 | ~20 | `--vus 100 --duration 1m` | 기본 동작 + Before/After 비교 |
| T2 | 1,000 | ~200 | `--vus 1000 --duration 3m` | 현재 구조 안정성 확인 |
| T3 | 10,000 | ~2,000 | `ramping-vus 0→10000, 5m` | 병목 탐색, 한계 확인 |
| T4 | 50,000+ | ~10,000 | `ramping-vus 0→50000, 5m` | 구조적 확장 필요 여부 판단 |

클라이언트는 5~10초 간격으로 `PUT /playback` 호출하는 것을 가정.

---

## 관측 지표

### API 지표 (k6에서 측정)

| 지표 | 측정 | 의미 |
|------|------|------|
| p95 응답시간 | `http_req_duration{p(95)}` | API thread가 blocking 없이 즉시 반환하는지 |
| 에러율 | `http_req_failed` | 비동기화 이후 에러가 사라졌는지 |
| throughput | `http_reqs` | 초당 처리량 |

### 내부 지표 (로그 / metrics에서 측정)

| 지표 | 의미 | 경고 신호 |
|------|------|-----------|
| Queue size 추이 | 소비 속도 ≥ 생산 속도인지 | 단조 증가 → 소비가 못 따라감 |
| Overflow 발생 횟수 | queue full 빈도 | 빈번 → capacity 부족 or 소비 속도 부족 |
| Flush 소요시간 | batch 1회 처리 시간 | > 500ms → DB 병목, 단일 SQL 전환 검토 |
| Coalescing 비율 | drain 건수 대비 coalesce 후 건수 | 높을수록 중복이 많았다는 뜻 |

### DB 지표

| 지표 | 측정 방법 | 의미 |
|------|-----------|------|
| 초당 commit 수 | `SHOW GLOBAL STATUS LIKE 'Com_commit'` | N번 → 1번으로 줄었는지 |
| 커넥션 사용량 | HikariCP metrics / `SHOW PROCESSLIST` | Leader만 사용하는지 |
| slow query | slow query log | batch UPDATE가 느려지는지 |

---

## Step 1+2 완료 후: 첫 번째 부하 테스트

### 목적

직접 DB 호출(Before)과 queue+bulk update(After)의 차이를 수치로 확인한다.

### 진행

1. **Before 측정**: queue 비활성화 (or 이전 브랜치)로 T1, T2 실행
2. **After 측정**: 현재 코드(queue+batchUpdate)로 T1, T2 실행
3. **비교**

### 관측 항목

| 지표 | Before 예상 | After 예상 | 왜 보는가 |
|------|------------|------------|-----------|
| API p95 | 10~50ms (DB IO 포함) | 1~5ms (queue.offer만) | 비동기 분리 효과 |
| 초당 commit | req/s와 동일 (~200) | ~1 (flush 주기당 1번) | bulk 효과 |
| DB 커넥션 점유 | 요청마다 1개 | Leader 1개만 | 커넥션 풀 절감 |
| 에러율 | 커넥션 풀 소진 시 발생 가능 | 0% | 안정성 |

### 판단 기준

- After가 Before보다 p95 응답시간이 확실히 낮으면 → 비동기 분리 성공
- Queue size가 안정적이면 → T2까지는 현재 구조로 충분
- Queue size가 T2에서 계속 증가하면 → Step 3(Coalescing) 효과 확인 후 재측정

---

## Step 3 완료 후: Coalescing 효과 측정

### 목적

중복 key 제거가 실제로 UPDATE 횟수를 얼마나 줄이는지 확인한다.

### 진행

T2, T3 단계에서 실행. Coalescing 전후 건수를 로그로 출력하여 비교.

```
// flush 로그 예시
[playback-flush] drained=1000, afterCoalesce=320, reduction=68%
```

### 관측 항목

| 지표 | 왜 보는가 | 판단 |
|------|-----------|------|
| Coalescing 비율 | 중복이 실제로 얼마나 발생하는지 | 50%+ 절감 → 효과 큼. 10% 미만 → 효과 미미 |
| Flush 소요시간 변화 | coalesce로 batch 크기가 줄어 flush가 빨라졌는지 | Step 2 대비 개선 정도 |
| Queue size 안정성 | coalesce가 소비 속도에 영향을 주는지 | 오히려 느려지면 안 됨 (coalesce 연산 비용) |

### 판단 기준

- Coalescing 비율이 높고, flush 시간이 줄었으면 → 효과 확인됨
- T3(10,000명)에서 Queue size가 안정적이면 → 현재 구조로 충분. Worker Pool 불필요
- T3에서 Queue size 증가 → 다음 중 판단:
  - flush 시간이 길다 → 단일 SQL 전환 or Worker Pool
  - offer 자체가 느리다 → Multi-Queue

---

## 최종 테스트 (Step 5 완료 후)

### 목적

모든 최적화(coalescing, event_time, 이중 트리거, retryBuffer) 적용 후 한계를 확인한다.

### 진행

T1 → T2 → T3 → T4 순서로 점진적으로 부하를 올린다.

### 관측 항목

T3, T4에서 집중 관측:

| 지표 | 경고 신호 | 다음 액션 |
|------|-----------|-----------|
| Queue size 단조 증가 | 소비 < 생산 | 어디가 병목인지 확인 (아래) |
| Flush 소요시간 > 500ms | DB IO 병목 | 단일 SQL(CASE WHEN) 전환 |
| Leader CPU 100% | 연산 병목 | Worker Pool 도입 |
| offer 지연 증가 | Queue 경합 | Multi-Queue (key-partitioned) |
| Overflow 빈번 | Queue capacity 부족 | capacity 증가 or 소비 속도 개선 |

### 진화 판단 흐름

```
Queue size 안정 → 현재 구조로 충분. 끝.

Queue size 계속 증가 → 어디가 병목?
  ├── flush가 느림 (DB IO)
  │     ├── batchUpdate → 단일 SQL 전환
  │     └── 그래도 느림 → Worker Pool (flush 병렬화)
  ├── flush는 빠른데 drain이 느림
  │     └── 이중 트리거 확인 (Step 5)
  └── offer 자체가 느림 (queue 경합)
        └── Multi-Queue (key-partitioned)

T4에서 flush + offer 둘 다 병목
  └── Multi-Queue + Worker Pool 조합
```

---

## k6 시나리오 참고

기존 `k6/scenarios/playback-upsert.js`를 기반으로 단계별 VU 수만 조정.

Before/After 비교 시 동일한 시나리오, 동일한 환경에서 실행해야 의미 있음.

```javascript
// T1
export const options = { vus: 100, duration: '1m' };

// T2
export const options = { vus: 1000, duration: '3m' };

// T3 (ramping)
export const options = {
  stages: [
    { duration: '1m', target: 2000 },
    { duration: '2m', target: 10000 },
    { duration: '2m', target: 10000 },
  ],
};

// T4 (ramping)
export const options = {
  stages: [
    { duration: '1m', target: 5000 },
    { duration: '2m', target: 50000 },
    { duration: '2m', target: 50000 },
  ],
};
```

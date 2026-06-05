# Step 1: Queue + Service 수정 (비동기 분리)

## 목적

`PUT /playback` 요청마다 DB를 직접 치던 구조에서, API thread는 in-memory queue에 command만 넣고 즉시 반환하도록 분리한다.

## Before / After

```
[Before]
Client → Controller → PlaybackService → PlaybackRepository.updatePlayback() → DB
                                          ↑ 매 요청마다 커넥션 점유 + commit

[After]
Client → Controller → PlaybackService → PlaybackCommandQueue.offer()  ← non-blocking
                                              ↓
                                    PlaybackFlushWorker (Leader Thread)
                                              ↓
                                    PlaybackRepository.updatePlayback() × N  ← 비동기
```

## 생성/수정 파일

### 신규 파일 (buffer 패키지)

| 파일 | 역할 |
|------|------|
| `PlaybackKey.java` | `record(memberId, contentsId)`. Coalescing key — 같은 사용자+콘텐츠 = 같은 key |
| `PlaybackCommand.java` | `record(memberId, contentsId, positionSec, requestedAt)`. Queue에 들어가는 command 단위. `key()` 메서드로 PlaybackKey 반환 |
| `PlaybackCommandQueue.java` | `LinkedBlockingQueue`(bounded) + `ConcurrentHashMap` overflow. API thread와 FlushWorker 사이의 버퍼 |
| `PlaybackFlushWorker.java` | Dedicated Leader Thread. queue에서 drain → 단건 UPDATE loop (Step 2에서 batchUpdate로 교체 예정) |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `PlaybackService.java` | `updatePlayback()`에서 `playbackRepository.updatePlayback()` → `playbackCommandQueue.offer()` + overflow fallback |
| `application.yml` | `playback.buffer.*` 설정 추가 |

## 핵심 구현 상세

### PlaybackCommandQueue

```java
@Component
public class PlaybackCommandQueue {
    private final BlockingQueue<PlaybackCommand> queue;          // LinkedBlockingQueue(bounded)
    private final ConcurrentHashMap<PlaybackKey, PlaybackCommand> overflowMap;  // queue full 대비
}
```

**왜 LinkedBlockingQueue인가:**
- `putLock`/`takeLock` 분리 → producer(API thread)와 consumer(FlushWorker) 간 경합 최소
- bounded → OOM 방지
- `drainTo()` 지원 → 한 번에 batch drain

**주요 메서드:**

#### `offer(memberId, contentsId, positionSec)` → boolean

API thread가 호출. `queue.offer(command)`로 non-blocking 삽입.

- 반환값: `true`(queue에 들어감) / `false`(queue full)
- `put()`이 아닌 `offer()`를 쓰는 이유: `put()`은 queue full 시 API thread를 **block** → 비동기화의 의미가 사라짐. `offer()`는 즉시 false를 반환하므로 API thread가 절대 블로킹되지 않음.

#### `offerToOverflow(memberId, contentsId, positionSec)` → void

queue full 시 호출. `ConcurrentHashMap.merge()`로 key별 최신값만 유지.

```java
overflowMap.merge(command.key(), command, (old, incoming) ->
    incoming.requestedAt().isAfter(old.requestedAt()) ? incoming : old);
```

**merge의 동작:**
1. key가 없으면 → 그냥 `put`
2. key가 이미 있으면 → remapping function으로 기존값(old)과 새값(incoming)을 비교
3. `requestedAt`이 더 늦은(최신) command만 남김

**왜 `put`이 아닌 `merge`인가:**
- `put`은 무조건 덮어쓰기 → 여러 API thread가 동시에 호출하면, 늦게 도착한 thread의 값이 반드시 최신이라는 보장 없음 (thread 스케줄링 순서 ≠ 요청 발생 순서)
- `merge`는 `requestedAt` 비교로 어떤 순서로 호출되든 최신값이 남음
- `ConcurrentHashMap.merge()`는 해당 key의 segment만 잠그므로 다른 key 접근과 경합 없음

#### `drain(bulkSize, timeoutMs)` → List<PlaybackCommand>

FlushWorker(Leader Thread)가 호출. queue에서 최대 bulkSize만큼 batch 추출.

```java
PlaybackCommand first = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
if (first != null) {
    batch.add(first);
    queue.drainTo(batch, bulkSize - 1);
}
```

**왜 `poll(timeout)` + `drainTo()` 2단계인가:**
1. `poll(timeout)`: queue가 비어있으면 최대 timeout까지 **blocking 대기**. 데이터가 들어오면 즉시 반환. → 빈 queue에서 CPU 낭비 없이 대기 (busy-wait 방지)
2. `drainTo(batch, bulkSize-1)`: 첫 번째 요소를 받은 시점에서, queue에 이미 쌓여있는 요소들을 **한 번에 non-blocking으로** 꺼냄. 내부적으로 takeLock 1번만 잡고 연속 추출.

**왜 `drainTo()` 한 번으로 안 되나:**
- `drainTo()`는 queue가 비어있으면 **즉시 빈 리스트 반환** (blocking 안 함)
- 저부하에서 queue가 비면 계속 빈 리스트 → 무한 루프 → CPU 100%
- `poll(timeout)`이 "최소 1개는 올 때까지 기다려"를 보장

**왜 `take()` 대신 `poll(timeout)`인가:**
- `take()`는 무한 대기 → `@PreDestroy shutdown()` 시 `interrupt()`로만 깨울 수 있음
- `poll(timeout)`은 주기적으로 loop 조건(`running`)을 체크할 수 있어 graceful shutdown에 유리

#### `drainOverflow()` → Map<PlaybackKey, PlaybackCommand>

FlushWorker가 flush 시 overflow map을 비우고 반환.

```java
Map<PlaybackKey, PlaybackCommand> snapshot = new ConcurrentHashMap<>(overflowMap);
overflowMap.keySet().removeAll(snapshot.keySet());
return snapshot;
```

**동작:**
1. 현재 overflowMap의 snapshot을 복사
2. snapshot에 있는 key만 원본에서 제거
3. snapshot 반환 → FlushWorker가 이 데이터를 flush

**snapshot 후 key별 제거하는 이유:**
- snapshot 생성과 제거 사이에 새 offer가 들어올 수 있음
- `clear()`를 쓰면 snapshot 이후에 들어온 데이터까지 삭제 → 유실
- `removeAll(snapshot.keySet())`은 snapshot 시점의 key만 제거 → 새로 들어온 데이터는 보존

**알려진 한계:** snapshot 생성 후 같은 key에 새 값이 merge되면, `removeAll`이 새 값까지 제거할 수 있음. Step 5에서 `remove(key, value)` (값까지 비교하는 제거)로 개선 예정.

---

**Overflow Map이 자기 제한적인 이유:**
- Map 크기 = 동시 활성 key(시청 세션) 수에 비례
- Queue처럼 요청 수에 비례하지 않음 → 10,000 동시 시청이면 최대 10,000 entry
- 같은 key에 새 요청이 오면 merge로 덮어쓰기 = 자연 coalescing → 크기가 더 커지지 않음

### PlaybackFlushWorker

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaybackFlushWorker {
    private final PlaybackCommandQueue commandQueue;
    private final PlaybackRepository playbackRepository;

    private volatile boolean running = true;
    private Thread leaderThread;  // daemon=true
}
```

- `@RequiredArgsConstructor`: 프로젝트 컨벤션에 맞게 Lombok 사용
- `@PostConstruct`/`@PreDestroy`: `InitializingBean`/`DisposableBean`보다 간결하고 Spring API 비결합

**왜 Dedicated Leader Thread인가:**
- `@Scheduled`는 fixedDelay 주기에 묶여서, bulkSize 도달해도 주기까지 대기
- Leader Thread는 drain에서 데이터 있으면 즉시 처리, 없으면 timeout 대기 → 이중 트리거 자연 지원 (Step 5)

**Leader Thread 생명주기:**

```
@PostConstruct init()
  → new Thread(this::leaderLoop, "playback-flush-leader")
  → daemon=true, start()

leaderLoop:
  while (running) {
      flush()  ← drain + DB write
  }
  → InterruptedException → break
  → 기타 Exception → log.error + 다음 루프 계속 (죽지 않음)
```

- `daemon=true`: JVM 종료 시 이 thread 때문에 종료가 막히지 않음
- `volatile boolean running`: `@PreDestroy shutdown()`에서 false로 바꾸면 다음 루프에서 탈출
- 예외 발생 시 thread가 죽지 않고 다음 flush를 계속 시도 → 일시적 DB 장애에도 worker가 살아있음

**Step 1의 flush 방식:**

```java
private void flush() throws InterruptedException {
    List<PlaybackCommand> drained = commandQueue.drain(bulkSize, flushTimeoutMs);
    if (drained.isEmpty()) return;

    for (PlaybackCommand cmd : drained) {
        playbackRepository.updatePlayback(cmd.memberId(), cmd.contentsId(), cmd.positionSec());
    }
}
```

- 단건 UPDATE loop: 각 command마다 독립적으로 `updatePlayback()` 호출
- 한 건이 실패해도 나머지는 처리됨 (개별 try-catch)
- Step 2에서 `PlaybackBatchRepository.batchUpdate()`로 교체하여 commit 1번으로 줄일 예정

**Graceful Shutdown:**

```
@PreDestroy shutdown()
  → running = false        ← 다음 루프에서 while 탈출
  → leaderThread.interrupt() ← poll(timeout) 대기 중이면 즉시 깨움
  → flush()                 ← best-effort: queue에 남아있는 데이터 마지막 처리 시도
```

- `interrupt()`가 필요한 이유: `poll(timeout)`이 blocking 중이면 `running=false`만으로는 즉시 반응하지 않음. interrupt로 `InterruptedException` 발생시켜 즉시 깨움
- best-effort flush: 남은 데이터를 최대한 처리하되, 실패하면 무시 (`catch (Exception ignored)`). 최대 유실량은 1초치 (flush-timeout-ms)

### PlaybackService 변경

```java
// Before
playbackRepository.updatePlayback(memberId, contentsId, positionSec);

// After
boolean offered = playbackCommandQueue.offer(memberId, contentsId, positionSec);
if (!offered) {
    playbackCommandQueue.offerToOverflow(memberId, contentsId, positionSec);
}
```

**왜 offer + offerToOverflow 2단계인가:**
1. 정상: `offer()` → queue에 들어감 → FlushWorker가 drain
2. queue full: `offer()` → false → `offerToOverflow()` → overflow map에 최신값만 유지
3. API thread 입장에서는 어느 경우든 **즉시 반환** (blocking 없음)

**왜 queue full 시 예외를 던지지 않는가:**
- Playback 위치 갱신은 "120초까지 봤는데 119초부터 재시작" 수준의 유실이 허용되는 도메인
- 503 반환보다 overflow map에 최신값이라도 유지하는 것이 사용자 경험에 유리
- overflow map이 자기 제한적(unique key 수에 bounded)이므로 메모리 폭발 위험도 없음

**`initPlayback()`은 왜 변경하지 않는가:**
- `initPlayback()`은 `INSERT IGNORE`로 최초 row를 생성하는 동작
- row가 존재해야 `updatePlayback()`이 동작하므로, INSERT는 동기로 즉시 실행되어야 함
- 호출 빈도도 "콘텐츠 재생 시작 시 1번"이므로 비동기화 이득이 없음

**클래스 레벨 `@Transactional` 유지 이유:**
- `updatePlayback()`은 DB를 직접 안 치고 queue에 넣기만 하므로 실질적 트랜잭션이 열리지 않음
- `initPlayback()`은 DB를 치므로 `@Transactional`이 필요
- 메서드별로 분리할 수도 있지만, 현재 구조에서 부작용이 없으므로 유지

### application.yml

```yaml
playback:
  buffer:
    queue-capacity: 100000    # LinkedBlockingQueue capacity
    bulk-size: 1000           # drain 시 최대 batch 크기
    flush-timeout-ms: 1000    # drain poll timeout
```

## 테스트

### PlaybackServiceTest (수정)

| 테스트 | 검증 |
|--------|------|
| `updatePlayback_offersToQueueWhenPlayableContentExists` | `offer()` 호출 verify + `playbackRepository.updatePlayback()` 호출 안 됨 verify |
| `updatePlayback_offersToOverflowWhenQueueFull` | `offer()` → false → `offerToOverflow()` 호출 verify |
| `updatePlayback_throwsWhenPlayableContentMissing` | 기존과 동일 |
| `initPlayback_*` | 변경 없음 |

### PlaybackCommandQueueTest (신규)

| 테스트 | 검증 |
|--------|------|
| `offer_returnsTrueWhenCapacityAvailable` | 여유 있을 때 true |
| `offer_returnsFalseWhenQueueFull` | capacity=1, 두 번째 offer → false |
| `drain_returnsEmptyWhenQueueEmptyAfterTimeout` | 빈 queue + 50ms timeout → 빈 리스트 |
| `drain_returnsBatchUpToBulkSize` | 5개 넣고 bulkSize=3 → 3개만 반환 |
| `offerToOverflow_keepsLatestByRequestedAt` | 같은 key 2번 → 나중 requestedAt의 값만 남음 |
| `drainOverflow_returnsEmptyMapWhenNoOverflow` | 빈 map → 빈 결과 |

## 이 단계의 한계 (다음 Step에서 해결)

| 한계 | 해결 Step |
|------|-----------|
| 단건 UPDATE loop → commit N번 | Step 2: batchUpdate (commit 1번) |
| 중복 key 제거 없음 | Step 3: Coalescing |
| event_time 정합성 없음 | Step 4: 조건부 UPDATE |
| timeout만으로 drain | Step 5: 이중 트리거 (bulkSize OR timeout) |
| retryBuffer 없음 | Step 5: 실패 시 재시도 |

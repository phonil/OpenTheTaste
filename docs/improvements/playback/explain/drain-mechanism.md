# drain() 동작 원리 — BlockingQueue의 poll(timeout)

## 핵심: poll(timeout)은 폴링이 아니라 이벤트 대기

```
poll(5초) = "5초마다 확인한다" (X)
poll(5초) = "잠들어 있다가, 아이템 오면 즉시 깨어난다. 5초 안에 안 오면 포기한다" (O)
```

## 시나리오 1: 큐 비어있을 때 아이템 도착

```
시간 ──────────────────────────────────────────────────>

Worker 스레드:
  poll(5초) 호출
  |
  |  [sleep 상태 — CPU 0%]
  |  .....
  |  .....          <── 1.2초 경과
  |
  |  ★ offer 발생! ──→ 즉시 깨어남 (1.2초만에)
  |
  cmd 반환
  drainTo()
  batchUpdate()
  다시 poll(5초)...

API 스레드:
                    queue.offer(cmd)
                    ↓
                    내부적으로 Worker를 깨움 (unpark)
```

**1.2초에 아이템 오면 1.2초에 깨어남. 5초 기다리는 거 아님.**

## 시나리오 2: 큐 비어있고 아무것도 안 올 때

```
시간 ──────────────────────────────────────────────────>

Worker 스레드:
  poll(5초) 호출
  |
  |  [sleep 상태 — CPU 0%]
  |  .....
  |  .....
  |  .....
  |  .....
  |  .....          <── 5초 경과, 아무것도 안 옴
  |
  null 반환
  빈 리스트 반환
  다시 poll(5초)...
```

## 시나리오 3: 큐에 이미 1500건 쌓여있을 때

```
시간 ──────────────────────────────────────────────────>

Worker 스레드:
  poll(5초) 호출
  |
  | 큐에 아이템 있음 → sleep 안 함 → 즉시 반환 (0ms)
  |
  cmd 1건 반환
  drainTo(999건) → 즉시 반환 (0ms)
  batch = 1000건
  while 탈출 (bulkSize 도달)
  batchUpdate()
  다시 poll...  → 큐에 500건 남아있음 → 또 즉시 반환
```

**고부하에서는 sleep 자체를 안 함. poll이 즉시 반환됨.**

## 시나리오 4: 저부하 — 띄엄띄엄 올 때

```
시간 ──────────────────────────────────────────────────>
     0s        1s        2s        3s        4s        5s

Worker:
  poll(5초)
  | [sleep]
  |    ★ offer → 깨어남
  |    cmd 1건, drainTo → 0건 추가 (큐 비어있음)
  |    batch=1건 < 1000 → while 계속
  |    poll(남은 4초)
  |    | [sleep]
  |    |              ★ offer → 깨어남
  |    |              cmd 1건, drainTo → 0건
  |    |              batch=2건 < 1000 → while 계속
  |    |              poll(남은 2초)
  |    |              | [sleep]
  |    |              |                          timeout!
  |    |              |                          null 반환
  |    |              |                          while 탈출
  batch=2건 반환
  batchUpdate(2건)
```

**5초 동안 모을 수 있는 만큼 모아서 한 번에 flush.**

## 내부 구조: 왜 CPU를 안 먹나

```
                    queue.offer(cmd)
                         |
                         v
              ┌─────────────────────┐
              │  LinkedBlockingQueue │
              │                     │
              │  내부 Condition:     │
              │  notEmpty.signal()  │──→ park된 스레드 깨움
              │                     │
              └─────────────────────┘
                         ^
                         |
                    queue.poll(timeout)
                         |
                    아이템 없으면:
                    LockSupport.park() ← OS 레벨 sleep
                    (CPU 스케줄러에서 빠짐)

                    아이템 offer되면:
                    LockSupport.unpark() ← OS가 깨움
                    (CPU 스케줄러에 복귀)
```

**busy-wait (while문 돌면서 계속 체크)가 아님.**
**OS 커널이 관리하는 sleep/wakeup 메커니즘.**

## 요약

| 질문 | 답 |
|------|-----|
| poll(5초)는 5초마다 확인? | 아님. 5초는 최대 대기 시간 |
| 아이템 오면 언제 깨어남? | 즉시 (offer하는 순간) |
| CPU 얼마나 먹음? | 대기 중 0%. 처리할 때만 잠깐 사용 |
| 고부하에서 sleep 함? | 안 함. 큐에 있으면 poll이 즉시 반환 |
| 저부하에서 어떻게 됨? | 5초 동안 오는 대로 모아서 한 번에 flush |

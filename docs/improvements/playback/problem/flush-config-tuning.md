# flush 설정값(bulk-size, flush-timeout-ms) 근거 부재

## 문제

현재 설정값에 근거가 없다. 감으로 잡은 값이며, 부하 테스트를 통해 확정해야 한다.

| 설정 | 현재 값 | 근거 |
|------|---------|------|
| `queue-capacity` | 100,000 | 없음 |
| `bulk-size` | 3,000 | 없음 |
| `flush-timeout-ms` | 3,000 | 없음 |

## 설정값이 동작에 미치는 영향

### flush-timeout-ms (poll 대기 시간)

Leader thread가 queue에서 첫 번째 아이템을 기다리는 최대 시간.

| 값 | 저부하 동작 | 고부하 동작 | 트레이드오프 |
|----|-----------|-----------|-------------|
| 짧음 (100ms) | 빈 drain이 잦음 (CPU 낭비) | 차이 없음 (즉시 반환) | 반응성 ↑, CPU ↓ |
| 김 (3000ms) | 대기 시간 길어짐 | 차이 없음 (즉시 반환) | 반응성 ↓, CPU 절약 |

- 고부하에서는 queue가 항상 차 있으므로 poll이 즉시 반환 → timeout 값이 무의미
- 저부하에서는 timeout이 곧 flush 주기가 됨 → 3초면 최대 3초 지연

### bulk-size (drain 최대 건수)

한 번 drain에서 꺼내는 최대 건수. batch UPDATE의 크기를 결정.

| 값 | 효과 | 트레이드오프 |
|----|------|-------------|
| 작음 (100) | batch 작음 → flush 빠름, 자주 실행 | commit 횟수 ↑ |
| 큼 (3000) | batch 큼 → flush 느림, 덜 자주 실행 | commit 횟수 ↓, 실패 시 유실 ↑ |

### queue-capacity

queue가 가득 차면 overflow map으로 전환. capacity가 크면 overflow 빈도 감소, 메모리 사용 증가.

## 현재 drain 동작의 특성

```
poll(timeout) → 첫 1건 도착 시 즉시 반환 → drainTo(bulkSize - 1)
```

| 상황 | 실제 batch 크기 |
|------|----------------|
| 포스트맨 1건 | 1건 (벌크 효과 없음) |
| 200 req/s | 수십~수백건 (flush 중 쌓인 만큼) |
| 10,000 req/s | 최대 bulk-size(3000)건 |

저부하에서 1건씩 처리되는 건 정상 동작이다. 벌크 효과는 부하가 올라가면 자연 발생한다.

## TODO

- [ ] k6 부하 테스트(T1~T3)에서 실제 drain 크기, flush 소요시간 측정
- [ ] 측정 결과에 따라 bulk-size, flush-timeout-ms 확정
- [ ] queue-capacity는 동시 시청자 수 × 평균 갱신 빈도 기반으로 산정

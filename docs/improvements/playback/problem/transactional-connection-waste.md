# 클래스 레벨 @Transactional의 커넥션 낭비 문제

## 문제

`PlaybackService`에 클래스 레벨 `@Transactional`이 걸려 있어서, DB를 사용하지 않는 `updatePlayback()`도 매 요청마다 커넥션을 점유하고 있었다.

```java
@Service
@Transactional  // ← 클래스 레벨: 모든 public 메서드에 적용
public class PlaybackService {

    // DB 사용 O → @Transactional 필요
    public void initPlayback(...) {
        playbackRepository.insertIgnorePlayback(...);
    }

    // DB 사용 X → queue.offer()만 함 → @Transactional 불필요
    public void updatePlayback(...) {
        playbackCommandQueue.offer(...);  // 커넥션 안 쓰는데 잡고 있음
    }
}
```

`updatePlayback()`이 queue.offer()만 하고 즉시 반환해야 하는데, `@Transactional` 때문에:
1. HikariCP에서 커넥션 획득
2. autoCommit = false
3. queue.offer() (커넥션 안 씀)
4. commit (빈 트랜잭션)
5. 커넥션 반환

비동기 분리의 핵심인 "커넥션 점유 제거"가 무효화되었다.

## 수치 근거 (k6 부하 테스트)

### @Transactional 제거 전 vs 후

| VU | 지표 | 제거 전 | 제거 후 |
|----|------|---------|---------|
| 1000 | p50 | 18.8ms | 16.0ms |
| 1000 | p95 | 35.5ms | 32.9ms |
| 2000 | p50 | 1367.4ms | **20.8ms** |
| 2000 | p95 | 10360.9ms | **195.4ms** |
| 2000 | 에러율 | 4.3% | **0%** |
| 2000 | WPS | 205.5/s | **377.9/s** |
| 3000 | 에러율 | 100% | 68.8% |

vu2000에서 p50이 1367ms → 20ms (65배), 에러율이 4.3% → 0%로 개선.

## 해결

클래스 레벨 `@Transactional` 제거, `initPlayback()`에만 메서드 레벨로 적용.

```java
@Service
public class PlaybackService {

    @Transactional
    public void initPlayback(...) { ... }  // DB 사용 → 트랜잭션 필요

    public void updatePlayback(...) { ... }  // queue.offer()만 → 트랜잭션 불필요
}
```

## 교훈

- 클래스 레벨 `@Transactional`은 DB를 사용하지 않는 메서드까지 커넥션을 잡는다
- 비동기 분리(queue)를 했더라도 `@Transactional`이 남아있으면 커넥션 절감 효과가 사라진다
- 메서드별로 필요한 곳에만 `@Transactional`을 건다

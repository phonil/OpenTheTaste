# Coalescing(Step 3) 필요성 재검토

## 결론

현재 설정에서 Coalescing은 불필요하다.

## 이유

클라이언트 갱신 주기(5초)가 flush 주기(최대 1초)보다 훨씬 길다.

| 항목 | 값 |
|------|-----|
| 클라이언트 갱신 주기 | 5초 |
| flush 주기 (저부하) | 최대 1초 (flush-timeout-ms) |
| flush 주기 (고부하) | ~100ms (flush 실행 시간) |

같은 (memberId, contentsId) key가 queue에 동시에 2개 이상 쌓이려면, flush되기 전에 같은 유저의 다음 갱신이 도착해야 한다. 그런데 다음 갱신은 5초 후이고, flush는 최대 1초 안에 일어나므로 중복이 발생하지 않는다.

```
t=0.000  User A 갱신 → queue에 들어감
t=0.100  flush → User A 꺼내서 DB 반영
t=5.000  User A 다음 갱신 → queue에 들어감 (이전 건은 이미 flush됨)
→ 같은 key가 queue에 동시에 존재하지 않음 → coalescing 할 대상이 없음
```

## Coalescing이 필요해지는 조건

- 클라이언트 갱신 주기 < flush 주기
  - 예: 갱신 주기 0.5초, flush 주기 1초 → 같은 key가 2개 쌓일 수 있음
- flush가 극단적으로 밀리는 상황
  - 예: DB 장애로 flush가 5초 이상 지연 → 같은 key가 queue에 누적

## 현재 결정

Step 3(Coalescing) 구현을 보류한다. 부하 테스트에서 실제로 같은 key 중복이 발생하는지 확인 후 필요 시 추가한다.

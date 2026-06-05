# k6 부하 테스트

API 성능 개선의 Before/After 비교를 위한 부하 테스트 환경.

---

## 구조

```
k6/
├── config.js                          ← 공통 설정 (URL, 임계값, VU 단계)
├── helpers/auth.js                    ← VU별 개별 JWT 토큰 관리
├── data/tokens.json                   ← 시드 사용자 1,000명 JWT (gitignore)
├── scenarios/home-screen.js           ← 홈 화면 시나리오 (6 API, 6 requests)
├── docker-compose.monitoring.yml      ← InfluxDB (k6 결과 저장)
├── run-all-before.ps1                 ← 일괄 측정 (PowerShell)
├── run-all-before.sh                  ← 일괄 측정 (Bash)
├── parse-results.ps1                  ← 결과 → md 보고서 (PowerShell)
├── parse-results.sh                   ← 결과 → md 보고서 (Bash)
└── results/                           ← 측정 결과 (gitignore)
    ├── before/{seed}/summary-{vu}.json
    ├── before/report.md
    ├── after/{seed}/summary-{vu}.json
    └── after/report.md
```

---

## 사전 조건

| 항목           | 확인 방법 |
|--------------|----------|
| k6 설치        | `k6 version` |
| jq 설치 (파싱용)? | `jq --version` (`winget install jqlang.jq`) |
| Docker 실행    | `docker ps` (ott-mysql 컨테이너) |
| 시드 메타데이터 적용  | `scripts/seed-metadata.sql` |
| 시드 프로시저 적용   | `scripts/seed-procedures.sql` |
| 토큰 생성 완료     | `k6/data/tokens.json` 존재 |
| api-user 실행  | `localhost:8080` 응답 |

### 토큰 생성 (최초 1회)

```powershell
# 시드 데이터 필요 (member 1~1000 존재)
$env:SPRING_PROFILES_ACTIVE="perf"; ./gradlew :apps:api-user:bootRun
# → k6/data/tokens.json 생성 후 앱 자동 종료
```

---

## Before/After 측정 흐름

```
1. Before 측정 (현재 성능 기록)
   │  .\k6\run-all-before.ps1
   │  → k6/results/before/{seed}/summary-{vu}.json
   │
   │  .\k6\parse-results.ps1
   │  → k6/results/before/report.md
   │
2. 코드 개선 (인덱스, 쿼리 최적화 등)
   │
3. api-user 재시작
   │
4. After 측정 (동일 조건 재측정)
   │  .\k6\run-all-before.ps1 -Phase after
   │  → k6/results/after/{seed}/summary-{vu}.json
   │
   │  .\k6\parse-results.ps1 -Phase after
   │  → k6/results/after/report.md
   │
5. report.md 비교 → 개선율 산출
```

---

## 실행 명령어 (프로젝트 루트에서)

### 일괄 측정

```powershell
# Before 전체 (시드 4개 x VU 5개 = 20회)
.\k6\run-all-before.ps1

# 원하는 조합만
.\k6\run-all-before.ps1 -Seeds "medium","large" -VuLevels "vu100","vu1000"

# 시드 1개 + VU 1개 (빠른 확인)
.\k6\run-all-before.ps1 -Seeds "small" -VuLevels "vu100"

# After 전체
.\k6\run-all-before.ps1 -Phase after

# After 원하는 조합만
.\k6\run-all-before.ps1 -Phase after -Seeds "medium" -VuLevels "vu500","vu1000"
```

### 결과 파싱 (md 보고서 생성)

```powershell
# Before 보고서
.\k6\parse-results.ps1

# After 보고서
.\k6\parse-results.ps1 -Phase after
```

### 단일 실행 (스크립트 없이 직접)

```powershell
# smoke (동작 확인)
k6 run --env LOAD=smoke .\k6\scenarios\home-screen.js

# 특정 VU
k6 run --env LOAD=vu1000 .\k6\scenarios\home-screen.js

# 특정 VU + 결과 저장
k6 run --env LOAD=vu1000 --summary-export=".\k6\results\before\medium\summary-vu1000.json" .\k6\scenarios\home-screen.js

# Grafana 모니터링 연동
k6 run --env LOAD=vu1000 --out influxdb=http://localhost:8086/k6 .\k6\scenarios\home-screen.js
```

---

## 파라미터

### run-all-before.ps1

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `-Phase` | `before` | 결과 저장 폴더명 |
| `-Seeds` | `small,medium,large,xl` | 테스트할 시드 프리셋 |
| `-VuLevels` | `vu100,vu500,vu1000,vu5000,vu10000` | 테스트할 VU 단계 |

### parse-results.ps1

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `-Phase` | `before` | 파싱할 결과 폴더명 |

---

## 시드 프리셋

| 프리셋 | member | media | watch_history | bookmark | likes | 소요 시간 |
|--------|--------|-------|--------------|----------|-------|----------|
| small | 100 | 1,000 | 10,000 | 5,000 | 5,000 | ~10초 |
| medium | 1,000 | 10,000 | 100,000 | 50,000 | 50,000 | ~1분 |
| large | 10,000 | 50,000 | 500,000 | 200,000 | 200,000 | ~10분 |
| xl | 10,000 | 500,000 | 5,000,000 | 2,000,000 | 2,000,000 | ~1시간+ |

---

## VU(부하) 단계

| 프리셋 | VU | 구간 |
|--------|-----|------|
| smoke | 1 | 10초 |
| vu100 | 100 | ramp 10s → sustain 1m → down 5s |
| vu500 | 500 | 동일 |
| vu1000 | 1,000 | 동일 |
| vu5000 | 5,000 | 동일 |
| vu10000 | 10,000 | 동일 |

---

## 시나리오: 홈 화면 진입

1 iteration = 홈 화면 진입 1회 = 6 HTTP requests:

| # | API | 설명 | 부하 특성 |
|---|-----|------|----------|
| 1 | `GET /playlists/trending` | 인기 차트 | 가벼움 (캐싱 후보) |
| 2 | `GET /playlists/recommend` | OO님이 좋아할만한 | 무거움 (동적 CASE WHEN) |
| 3 | `GET /playlists/tags/top?index=0` | 선호태그 1순위 | 중간 |
| 4 | `GET /playlists/tags/top?index=1` | 선호태그 2순위 | 중간 |
| 5 | `GET /playlists/tags/top?index=2` | 선호태그 3순위 | 중간 |
| 6 | `GET /playlists/history` | 시청이력 | 무거움 (JOIN+GROUP BY) |

---

## 측정 지표

| 지표 | 의미 | 임계값 |
|------|------|--------|
| `http_req_duration` p50 | 절반의 요청 응답 시간 | < 200ms |
| `http_req_duration` p95 | 95% 요청 응답 시간 | < 500ms |
| `http_req_duration` p99 | 99% 요청 응답 시간 | < 1000ms |
| `http_req_failed` | 에러율 | < 1% |
| `http_reqs` | 초당 처리량 (RPS) | - |
| `group_duration` | 홈 화면 전체 로드 시간 | - |

API별 분리: `http_req_duration{name:trending}`, `{name:recommend}` 등 tag로 확인.

---

## 결과 보고서 예시 (report.md)

```markdown
## 시드: medium

### 전체 요약 (홈 화면 전체 응답 시간)

| VU | p50 | p95 | p99 | 에러율 | RPS |
|----|-----|-----|-----|--------|-----|
| vu100 | 45.2ms | 120.5ms | 250.3ms | 0.0000 | 85.2/s |
| vu500 | 78.1ms | 310.4ms | 520.8ms | 0.0012 | 320.1/s |
| vu1000 | 150.3ms | 580.2ms | 1020.5ms | 0.0085 | 550.3/s |

### API별 p95 응답 시간 (ms)

| VU | trending | recommend | tags_top_0 | tags_top_1 | tags_top_2 | history |
|----|----------|-----------|------------|------------|------------|---------|
| vu100 | 25.1 | 85.3 | 40.2 | 38.5 | 39.1 | 55.2 |
| vu500 | 45.2 | 320.1 | 110.5 | 108.3 | 109.8 | 180.4 |
```

---

## 스크립트 동작 상세

### run-all-before.ps1 내부 흐름

```
시드 배열 순회:
  1. docker exec → clean-data.sql (DB 초기화)
  2. docker exec → CALL seed_all('{seed}') (시드 생성)
  3. 워밍업 대기 10초
  4. VU 배열 순회:
     - k6 run --summary-export → summary JSON 저장
     - 테스트 간 대기 10초
```

### parse-results.ps1 내부 흐름

```
시드 디렉토리 순회:
  1. summary JSON에서 jq로 수치 추출 (p50, p95, p99, 에러율, RPS)
  2. API별 태그 메트릭에서 p95 추출
  3. Markdown 테이블 조립
  4. report.md로 출력
```

---

## 주의사항

| 항목 | 내용 |
|------|------|
| 동일 조건 | Before/After는 반드시 같은 시드, 같은 VU로 비교 |
| 프로젝트 루트 | 모든 명령어는 프로젝트 루트에서 실행 |
| xl 시드 | 시드 생성만 ~1시간. 시간 여유 있을 때 실행 |
| VU 10,000 | 로컬 머신 한계로 k6 자체 CPU 부족 가능. vu5000까지 권장 |
| 앱 재시작 | After 측정 전 코드 개선 적용 후 api-user 재시작 필요 |
| JWT 만료 | access-token-expiry 14일 (테스트 기간 내 만료 없음) |
| gitignore | `k6/results/`, `k6/data/`는 gitignore 처리됨 |

---

## Grafana 모니터링 (선택)

```powershell
# InfluxDB 실행
docker-compose -f k6/docker-compose.monitoring.yml up -d

# k6 실행 시 --out 추가
k6 run --env LOAD=vu1000 --out influxdb=http://localhost:8086/k6 .\k6\scenarios\home-screen.js
```

기존 Grafana (http://localhost:3001)에서:
- Data Sources → Add → InfluxDB → URL: `http://k6-influxdb:8086`, Database: `k6`
- Dashboards → Import → ID: `2587`

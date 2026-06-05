#!/bin/bash
# =============================================================
# Before 전체 일괄 측정 스크립트
#
# 시드 4개 x VU 5개 = 20회 테스트 실행
# 각 시드별로 DB 초기화 → 재시드 → 테스트 5회 → 다음 시드
#
# 사용법:
#   chmod +x k6/run-all-before.sh
#   ./k6/run-all-before.sh          # before (기본)
#   ./k6/run-all-before.sh after    # after
#
# 사전 조건:
#   - k6 설치됨 (k6 version)
#   - Docker 실행 중 (ott-mysql 컨테이너)
#   - api-user 앱 실행 중 (localhost:8080)
#   - 토큰 생성 완료 (k6/data/tokens.json)
#   - scripts/seed-metadata.sql, seed-procedures.sql 적용 완료
#
# 출력:
#   k6/results/{phase}/{seed}/summary-{vu}.json
# =============================================================
# ●─1. 원하는 조합만 실행   ────────────────────────────────────────────────────────────────
#  스크립트 상단의 배열을 수정하면 됨:
#  # 예: medium, large만 + vu100, vu1000만
#──────────────────────────────────────────────────────────────────────────────────────────
#  SEEDS=("medium" "large")
#  VU_LEVELS=("vu100" "vu1000")

set -e

SEEDS=("small" "medium" "large" "xl")
VU_LEVELS=("vu100" "vu500" "vu1000" "vu5000" "vu10000")
PHASE=${1:-before}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================"
echo " Phase: $PHASE"
echo " Seeds: ${SEEDS[*]}"
echo " VU Levels: ${VU_LEVELS[*]}"
echo " Total runs: $((${#SEEDS[@]} * ${#VU_LEVELS[@]}))"
echo "============================================"
echo ""

run_count=0
total_runs=$((${#SEEDS[@]} * ${#VU_LEVELS[@]}))

for seed in "${SEEDS[@]}"; do
    echo ""
    echo "========================================"
    echo " SEED: $seed"
    echo "========================================"

    # 1. DB 초기화
    echo "[1/3] 데이터 삭제 중..."
    docker exec -i ott-mysql mysql -u ott -pottpw ott < "$PROJECT_ROOT/scripts/clean-data.sql"

    # 2. 시드 생성
    echo "[2/3] 시드 생성 중 ($seed)..."
    docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('$seed');"

    # 3. 앱 워밍업 대기 (시드 교체 후 커넥션 풀 안정화)
    echo "[3/3] 워밍업 대기 (10초)..."
    sleep 10

    # 결과 디렉토리 생성
    mkdir -p "$SCRIPT_DIR/results/$PHASE/$seed"

    # 4. VU별 테스트 실행
    for vu in "${VU_LEVELS[@]}"; do
        run_count=$((run_count + 1))
        echo ""
        echo "--- [$run_count/$total_runs] $seed @ $vu ---"

        k6 run --env LOAD=$vu \
            --summary-export="$SCRIPT_DIR/results/$PHASE/$seed/summary-${vu}.json" \
            "$SCRIPT_DIR/scenarios/home-screen.js"

        echo "--- 완료. 다음 테스트 전 대기 (30초) ---"
        sleep 30
    done
done

echo ""
echo "============================================"
echo " 전체 완료! ($total_runs회)"
echo " 결과: k6/results/$PHASE/"
echo "============================================"

#!/bin/bash
# =============================================================
# k6 결과 파싱 → Markdown 테이블 자동 생성
#
# --summary-export로 저장된 JSON에서 수치를 추출하여
# Before/After 비교용 마크다운 테이블을 생성한다.
#
# 사용법:
#   chmod +x k6/parse-results.sh
#   ./k6/parse-results.sh before        # before 결과 파싱
#   ./k6/parse-results.sh after         # after 결과 파싱
#
# 사전 조건:
#   - jq 설치됨 (https://jqlang.github.io/jq/)
#   - k6/results/{phase}/{seed}/summary-{vu}.json 존재
#
# 출력:
#   k6/results/{phase}/report.md
# =============================================================

set -e

PHASE=${1:-before}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results/$PHASE"
OUTPUT="$RESULTS_DIR/report.md"

SEEDS=("small" "medium" "large" "xl")
VU_LEVELS=("vu100" "vu500" "vu1000" "vu5000" "vu10000")
API_NAMES=("trending" "recommend" "tags_top_0" "tags_top_1" "tags_top_2" "history")

# jq 확인
if ! command -v jq &> /dev/null; then
    echo "ERROR: jq가 설치되어 있지 않습니다."
    echo "  Windows: winget install jqlang.jq"
    echo "  Mac: brew install jq"
    exit 1
fi

echo "# 홈 화면 API 성능 측정 결과 ($PHASE)" > "$OUTPUT"
echo "" >> "$OUTPUT"
echo "측정 일시: $(date '+%Y-%m-%d %H:%M')" >> "$OUTPUT"
echo "" >> "$OUTPUT"

for seed in "${SEEDS[@]}"; do
    echo "## 시드: $seed" >> "$OUTPUT"
    echo "" >> "$OUTPUT"

    # --- 전체 요약 테이블 ---
    echo "### 전체 요약 (홈 화면 전체 응답 시간)" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
    echo "| VU | p50 | p95 | p99 | 에러율 | RPS |" >> "$OUTPUT"
    echo "|----|-----|-----|-----|--------|-----|" >> "$OUTPUT"

    for vu in "${VU_LEVELS[@]}"; do
        file="$RESULTS_DIR/$seed/summary-${vu}.json"
        if [ ! -f "$file" ]; then
            echo "| $vu | - | - | - | - | - |" >> "$OUTPUT"
            continue
        fi

        p50=$(jq -r '.metrics.http_req_duration.values.med // "-"' "$file" | xargs printf "%.1f")
        p95=$(jq -r '.metrics.http_req_duration.values["p(95)"] // "-"' "$file" | xargs printf "%.1f")
        p99=$(jq -r '.metrics.http_req_duration.values["p(99)"] // "-"' "$file" | xargs printf "%.1f")
        err=$(jq -r '.metrics.http_req_failed.values.rate // "-"' "$file" | xargs printf "%.4f")
        rps=$(jq -r '.metrics.http_reqs.values.rate // "-"' "$file" | xargs printf "%.1f")

        echo "| $vu | ${p50}ms | ${p95}ms | ${p99}ms | ${err} | ${rps}/s |" >> "$OUTPUT"
    done

    echo "" >> "$OUTPUT"

    # --- API별 테이블 ---
    echo "### API별 p95 응답 시간 (ms)" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
    echo "| VU | trending | recommend | tags_top_0 | tags_top_1 | tags_top_2 | history |" >> "$OUTPUT"
    echo "|----|----------|-----------|------------|------------|------------|---------|" >> "$OUTPUT"

    for vu in "${VU_LEVELS[@]}"; do
        file="$RESULTS_DIR/$seed/summary-${vu}.json"
        if [ ! -f "$file" ]; then
            echo "| $vu | - | - | - | - | - | - |" >> "$OUTPUT"
            continue
        fi

        row="| $vu"
        for api in "${API_NAMES[@]}"; do
            val=$(jq -r ".metrics[\"http_req_duration{name:$api}\"].values[\"p(95)\"] // \"-\"" "$file" 2>/dev/null)
            if [ "$val" != "-" ] && [ "$val" != "null" ]; then
                val=$(printf "%.1f" "$val")
            else
                val="-"
            fi
            row="$row | $val"
        done
        row="$row |"
        echo "$row" >> "$OUTPUT"
    done

    echo "" >> "$OUTPUT"
    echo "---" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
done

echo "완료: $OUTPUT"

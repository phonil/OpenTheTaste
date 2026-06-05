# =============================================================
# Before 전체 일괄 측정 스크립트 (PowerShell)
#
# 시드 4개 x VU 5개 = 20회 테스트 실행
#
# 사용법 (프로젝트 루트에서 실행):
#   .\k6\run-all-before.ps1              # before (기본)
#   .\k6\run-all-before.ps1 -Phase after # after
#   .\k6\run-all-before.ps1 -Seeds "medium","large" -VuLevels "vu100","vu1000"
#
# 사전 조건:
#   - k6 설치됨 (k6 version)
#   - Docker 실행 중 (ott-mysql 컨테이너)
#   - api-user 앱 실행 중 (localhost:8080)
#   - 토큰 생성 완료 (k6/data/tokens.json)
#   - scripts/seed-metadata.sql, seed-procedures.sql 적용 완료
# =============================================================

param(
    [string]$Phase = "before",
    [string[]]$Seeds = @("small", "medium", "large", "xl"),
    [string[]]$VuLevels = @("vu100", "vu500", "vu1000", "vu5000", "vu10000")
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$totalRuns = $Seeds.Count * $VuLevels.Count
$runCount = 0

Write-Host "============================================"
Write-Host " Phase: $Phase"
Write-Host " Seeds: $($Seeds -join ', ')"
Write-Host " VU Levels: $($VuLevels -join ', ')"
Write-Host " Total runs: $totalRuns"
Write-Host "============================================"
Write-Host ""

foreach ($seed in $Seeds) {
    Write-Host ""
    Write-Host "========================================"
    Write-Host " SEED: $seed"
    Write-Host "========================================"

    # 1. DB 초기화
    Write-Host "[1/3] 데이터 삭제 중..."
    cmd /c "docker exec -i ott-mysql mysql -u ott -pottpw ott < .\scripts\clean-data.sql"

    # 2. 시드 생성
    Write-Host "[2/3] 시드 생성 중 ($seed)..."
    docker exec ott-mysql mysql -u ott -pottpw ott -e "CALL seed_all('$seed');"

    # 3. 워밍업 대기
    Write-Host "[3/3] 워밍업 대기 (10초)..."
    Start-Sleep -Seconds 10

    # 결과 디렉토리 생성
    $resultDir = ".\k6\results\$Phase\$seed"
    New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

    # 4. VU별 테스트 실행
    foreach ($vu in $VuLevels) {
        $runCount++
        Write-Host ""
        Write-Host "--- [$runCount/$totalRuns] $seed @ $vu ---"

        k6 run --env LOAD=$vu `
            --summary-export="$resultDir\summary-${vu}.json" `
            .\k6\scenarios\home-screen.js

        Write-Host "--- 완료. 다음 테스트 전 대기 (10초) ---"
        Start-Sleep -Seconds 10
    }
}

Write-Host ""
Write-Host "============================================"
Write-Host " 전체 완료! (${totalRuns}회)"
Write-Host " 결과: .\k6\results\$Phase\"
Write-Host "============================================"

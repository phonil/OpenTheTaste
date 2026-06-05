# =============================================================
# k6 결과 파싱 -> Markdown 테이블 자동 생성 (PowerShell)
#
# 사용법 (프로젝트 루트에서 실행):
#   .\k6\parse-results.ps1                # before (기본)
#   .\k6\parse-results.ps1 -Phase after   # after
#
# 사전 조건:
#   - .\k6\results\{phase}\{seed}\summary-{vu}.json 존재
#   - jq 불필요 (PowerShell 네이티브 JSON 파싱)
#
# k6/results/{phase}/ 안에 있는 모든 시드 폴더를 자동으로 찾아서 전부 파싱함.
#
# 출력:
#   .\k6\results\{phase}\report.md
# =============================================================

param(
    [string]$Phase = "before"
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ResultsDir = ".\k6\results\$Phase"
$Output = "$ResultsDir\report.md"

$Seeds = @("small", "medium", "large", "xl")
$VuLevels = @("vu100", "vu500", "vu1000", "vu5000", "vu10000")
$ApiNames = @("trending", "recommend", "tags_top_0", "tags_top_1", "tags_top_2", "history")
$DurationFields = @("min", "avg", "med", "p(90)", "p(95)", "max")

function Get-Metric {
    param($Json, [string]$MetricName, [string]$Field)
    $metric = $Json.metrics.PSObject.Properties | Where-Object { $_.Name -eq $MetricName } | Select-Object -First 1
    if (-not $metric) { return $null }
    $value = $metric.Value.PSObject.Properties | Where-Object { $_.Name -eq $Field } | Select-Object -First 1
    if (-not $value) { return $null }
    return $value.Value
}

function Format-Val {
    param($Value, [string]$Fmt = "F1")
    if ($null -eq $Value) { return "-" }
    return ("{0:$Fmt}" -f ([double]$Value))
}

$report = @()
$report += "# Home Screen API Performance Results ($Phase)"
$report += ""
$report += "Measured: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
$report += ""

foreach ($seed in $Seeds) {
    $seedDir = "$ResultsDir\$seed"
    if (-not (Test-Path $seedDir)) { continue }

    $report += "## Seed: $seed"
    $report += ""

    # =============================================
    # 1. 전체 요약 (http_req_duration 전체)
    # =============================================
    $report += "### 1. Overall http_req_duration (ms)"
    $report += ""
    $report += "| VU | min | avg | med(p50) | p90 | p95 | max | RPS | Error Rate | Total Reqs |"
    $report += "|----|-----|-----|----------|-----|-----|-----|-----|------------|------------|"

    foreach ($vu in $VuLevels) {
        $file = "$seedDir\summary-${vu}.json"
        if (-not (Test-Path $file)) {
            $report += "| $vu | - | - | - | - | - | - | - | - | - |"
            continue
        }

        $json = Get-Content $file -Raw | ConvertFrom-Json

        $min = Get-Metric -Json $json -MetricName "http_req_duration" -Field "min"
        $avg = Get-Metric -Json $json -MetricName "http_req_duration" -Field "avg"
        $med = Get-Metric -Json $json -MetricName "http_req_duration" -Field "med"
        $p90 = Get-Metric -Json $json -MetricName "http_req_duration" -Field "p(90)"
        $p95 = Get-Metric -Json $json -MetricName "http_req_duration" -Field "p(95)"
        $max = Get-Metric -Json $json -MetricName "http_req_duration" -Field "max"
        $rps = Get-Metric -Json $json -MetricName "http_reqs" -Field "rate"
        $errRate = Get-Metric -Json $json -MetricName "http_req_failed" -Field "value"
        $totalReqs = Get-Metric -Json $json -MetricName "http_reqs" -Field "count"

        $report += "| $vu | $(Format-Val $min) | $(Format-Val $avg) | $(Format-Val $med) | $(Format-Val $p90) | $(Format-Val $p95) | $(Format-Val $max) | $(Format-Val $rps)/s | $(Format-Val $errRate 'F4') | $(Format-Val $totalReqs 'F0') |"
    }

    $report += ""

    # =============================================
    # 2. 홈 화면 전체 로드 시간 (group_duration)
    # =============================================
    $report += "### 2. Group Duration - Home Screen Total Load (ms)"
    $report += ""
    $report += "| VU | min | avg | med(p50) | p90 | p95 | max |"
    $report += "|----|-----|-----|----------|-----|-----|-----|"

    foreach ($vu in $VuLevels) {
        $file = "$seedDir\summary-${vu}.json"
        if (-not (Test-Path $file)) {
            $report += "| $vu | - | - | - | - | - | - |"
            continue
        }

        $json = Get-Content $file -Raw | ConvertFrom-Json

        $min = Get-Metric -Json $json -MetricName "group_duration" -Field "min"
        $avg = Get-Metric -Json $json -MetricName "group_duration" -Field "avg"
        $med = Get-Metric -Json $json -MetricName "group_duration" -Field "med"
        $p90 = Get-Metric -Json $json -MetricName "group_duration" -Field "p(90)"
        $p95 = Get-Metric -Json $json -MetricName "group_duration" -Field "p(95)"
        $max = Get-Metric -Json $json -MetricName "group_duration" -Field "max"

        $report += "| $vu | $(Format-Val $min) | $(Format-Val $avg) | $(Format-Val $med) | $(Format-Val $p90) | $(Format-Val $p95) | $(Format-Val $max) |"
    }

    $report += ""

    # =============================================
    # 3. API별 상세 (각 API의 전체 필드)
    # =============================================
    foreach ($api in $ApiNames) {
        $metricName = "http_req_duration{name:$api}"

        $report += "### 3-$($ApiNames.IndexOf($api) + 1). $api (ms)"
        $report += ""
        $report += "| VU | min | avg | med(p50) | p90 | p95 | max |"
        $report += "|----|-----|-----|----------|-----|-----|-----|"

        foreach ($vu in $VuLevels) {
            $file = "$seedDir\summary-${vu}.json"
            if (-not (Test-Path $file)) {
                $report += "| $vu | - | - | - | - | - | - |"
                continue
            }

            $json = Get-Content $file -Raw | ConvertFrom-Json

            $min = Get-Metric -Json $json -MetricName $metricName -Field "min"
            $avg = Get-Metric -Json $json -MetricName $metricName -Field "avg"
            $med = Get-Metric -Json $json -MetricName $metricName -Field "med"
            $p90 = Get-Metric -Json $json -MetricName $metricName -Field "p(90)"
            $p95 = Get-Metric -Json $json -MetricName $metricName -Field "p(95)"
            $max = Get-Metric -Json $json -MetricName $metricName -Field "max"

            $report += "| $vu | $(Format-Val $min) | $(Format-Val $avg) | $(Format-Val $med) | $(Format-Val $p90) | $(Format-Val $p95) | $(Format-Val $max) |"
        }

        $report += ""
    }

    # =============================================
    # 4. Iterations & Throughput
    # =============================================
    $report += "### 4. Iterations & Throughput"
    $report += ""
    $report += "| VU | Iterations | Iter/s | Total Reqs | RPS |"
    $report += "|----|------------|--------|------------|-----|"

    foreach ($vu in $VuLevels) {
        $file = "$seedDir\summary-${vu}.json"
        if (-not (Test-Path $file)) {
            $report += "| $vu | - | - | - | - |"
            continue
        }

        $json = Get-Content $file -Raw | ConvertFrom-Json

        $iterCount = Get-Metric -Json $json -MetricName "iterations" -Field "count"
        $iterRate = Get-Metric -Json $json -MetricName "iterations" -Field "rate"
        $reqCount = Get-Metric -Json $json -MetricName "http_reqs" -Field "count"
        $reqRate = Get-Metric -Json $json -MetricName "http_reqs" -Field "rate"

        $report += "| $vu | $(Format-Val $iterCount 'F0') | $(Format-Val $iterRate)/s | $(Format-Val $reqCount 'F0') | $(Format-Val $reqRate)/s |"
    }

    $report += ""
    $report += "---"
    $report += ""
}

# UTF-8 without BOM
$outputPath = (Resolve-Path $ResultsDir).Path + "\report.md"
[System.IO.File]::WriteAllLines($outputPath, $report, [System.Text.UTF8Encoding]::new($false))
Write-Host "완료: $Output"

<#
.SYNOPSIS
    Loadtest 캠페인을 DRAFT → ACTIVE 로 전환하고 stats 를 poll 한다.

.DESCRIPTION
    1) POST /api/v1/auth/login 으로 JWT 발급
    2) PATCH /api/v1/projects/{pid}/campaigns/{cid}/status { "status": "ACTIVE" }
       → CampaignActivator 가 campaign.send.requested 토픽으로 발행
    3) GET /api/v1/projects/{pid}/campaigns/{cid}/logs 의 카운트를 N 초마다 출력
       (notification_logs 적재 진행 상황 확인용)

    민감 정보(비밀번호)는 인자로 받지 않고 환경변수 BUTTERFLY_PASSWORD 에서 읽는다.

.EXAMPLE
    $env:BUTTERFLY_PASSWORD = '802025ym12!@'
    .\trigger-campaign.ps1 -ProjectId 5 -CampaignId 42

.NOTES
    PowerShell 5.1 호환. ConvertTo-Json 의 문자열 래핑 버그를 피하기 위해
    request body 는 단일 인용 here-string 으로 직접 작성한다.
#>
param(
    [Parameter(Mandatory=$true)][long]$ProjectId,
    [Parameter(Mandatory=$true)][long]$CampaignId,
    [string]$BaseUrl    = 'https://k14b101.p.ssafy.io',
    [string]$Email      = 'leeseoyeon10116@gmail.com',
    [int]$PollSeconds   = 60,
    [int]$PollIntervalSec = 5
)

$ErrorActionPreference = 'Stop'

# ─────────────────────────────────────────────────────────────────────────────
# 0. 비밀번호 / TLS 준비
# ─────────────────────────────────────────────────────────────────────────────
$password = $env:BUTTERFLY_PASSWORD
if (-not $password) {
    throw "환경변수 BUTTERFLY_PASSWORD 를 먼저 설정하세요. 예: `$env:BUTTERFLY_PASSWORD = '...'"
}

# PS 5.1 기본 TLS 1.0 회피 — TLS 1.2 강제
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ─────────────────────────────────────────────────────────────────────────────
# 1. 로그인 → accessToken 발급
# ─────────────────────────────────────────────────────────────────────────────
Write-Host "[1/3] POST $BaseUrl/api/v1/auth/login (email=$Email)"

# 비밀번호에 작은따옴표 / 백슬래시 / 큰따옴표 등이 들어 있을 가능성을 고려해 JSON 직조립
$emailJson    = '"' + ($Email    -replace '\\', '\\' -replace '"', '\"') + '"'
$passwordJson = '"' + ($password -replace '\\', '\\' -replace '"', '\"') + '"'
$loginBody = '{"email":' + $emailJson + ',"password":' + $passwordJson + '}'

$loginResp = Invoke-RestMethod -Method POST `
    -Uri "$BaseUrl/api/v1/auth/login" `
    -Body $loginBody `
    -ContentType 'application/json; charset=utf-8'

# 응답이 ApiResponse<LoginResponse> 래핑이면 .data, 아니면 raw
$loginData = if ($loginResp.data) { $loginResp.data } else { $loginResp }
$token = $loginData.accessToken
if (-not $token) { throw "accessToken 을 응답에서 찾지 못했습니다. 응답: $($loginResp | ConvertTo-Json -Depth 5 -Compress)" }
Write-Host "    accessToken 획득 (length=$($token.Length))"

$authHeaders = @{ Authorization = "Bearer $token" }

# ─────────────────────────────────────────────────────────────────────────────
# 2. DRAFT → ACTIVE 전환
# ─────────────────────────────────────────────────────────────────────────────
$statusUri = "$BaseUrl/api/v1/projects/$ProjectId/campaigns/$CampaignId/status"
Write-Host "[2/3] PATCH $statusUri  body={`"status`":`"ACTIVE`"}"

$activateBody = '{"status":"ACTIVE"}'
$activateStart = Get-Date
$activateResp = Invoke-RestMethod -Method PATCH `
    -Uri $statusUri `
    -Headers $authHeaders `
    -Body $activateBody `
    -ContentType 'application/json; charset=utf-8'

Write-Host "    응답: $($activateResp | ConvertTo-Json -Depth 5 -Compress)"
Write-Host "    Kafka campaign.send.requested 발행 후 worker 가 200건 fan-out 시작 예상"

# ─────────────────────────────────────────────────────────────────────────────
# 3. notification_logs 카운트 poll
# ─────────────────────────────────────────────────────────────────────────────
$logsUri = "$BaseUrl/api/v1/projects/$ProjectId/campaigns/$CampaignId/logs?page=0&size=1"
Write-Host "[3/3] poll $logsUri (${PollIntervalSec}s 간격, 최대 ${PollSeconds}s)"

$deadline = (Get-Date).AddSeconds($PollSeconds)
$lastTotal = -1
while ((Get-Date) -lt $deadline) {
    try {
        $logsResp = Invoke-RestMethod -Method GET -Uri $logsUri -Headers $authHeaders
        $logsData = if ($logsResp.data) { $logsResp.data } else { $logsResp }
        # 응답이 Page 형태면 totalElements, 아니면 size 키 다양
        $total = if ($logsData.totalElements -ne $null) { $logsData.totalElements }
                 elseif ($logsData.total -ne $null) { $logsData.total }
                 elseif ($logsData.totalCount -ne $null) { $logsData.totalCount }
                 else { $null }

        if ($total -ne $lastTotal) {
            $elapsed = [int]((Get-Date) - $activateStart).TotalSeconds
            Write-Host ("    +{0,3}s  notification_logs total = {1}" -f $elapsed, $total)
            $lastTotal = $total
            if ($total -ge 200) {
                Write-Host "    200건 적재 완료 — verify.sql 로 status 분포 확인하세요."
                break
            }
        }
    } catch {
        Write-Host "    poll error: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds $PollIntervalSec
}

Write-Host ""
Write-Host "트리거 완료. 다음을 직접 확인:"
Write-Host "  - K3s 마스터에서 verify.sql 실행"
Write-Host "  - 에뮬레이터 5556/5558 에서 adb logcat | sls FCM_Main"
Write-Host "  - kubectl logs -n butterfly -l app=butterfly-worker --tail=200 | sls DeliveryKafkaConsumer"

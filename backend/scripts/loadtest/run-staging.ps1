<#
.SYNOPSIS
    로컬 docker-compose staging 환경에 OWNER + Project + FCM credential + loadtest 시드를 적용한다.

.DESCRIPTION
    전제:
      - docker compose up -d (mysql/redis/kafka) 가 동작 중
      - butterfly-api 가 한 번 부팅되어 channel_credentials 테이블이 Hibernate ddl-auto 로 생성된 상태
      - Firebase Service Account JSON 이 -FcmJsonPath 에 존재

    실행 순서:
      1) 00-staging-bootstrap.sql.template 의 __REPLACE_WITH_SERVICE_ACCOUNT_JSON__ 을
         FcmJsonPath 의 한 줄 JSON 으로 치환 → docker exec butterfly-mysql mysql 로 적용
      2) 01-loadtest-users-devices.sql / 02-loadtest-segment.sql / 03-loadtest-campaign.sql 에서
         ${PROJECT_ID}=1, ${MEMBER_ID}=1 치환 후 순차 적용

.EXAMPLE
    .\run-staging.ps1
    .\run-staging.ps1 -FcmJsonPath C:\path\to\firebase-adminsdk.json
#>
param(
    [string]$FcmJsonPath = "C:\Users\SSAFY\Downloads\butterfly-test-ce57c-firebase-adminsdk-fbsvc-6f6aaa8981.json",
    [string]$MysqlContainer = "butterfly-mysql",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "root",
    [string]$MysqlDb = "butterfly",
    [int]$ProjectId = 1,
    [int]$MemberId = 1
)

# PS 5.1 의 NativeCommandError 가 mysql 의 stderr warning("Using a password...") 도 fatal 로 보기 때문에
# ErrorActionPreference 는 Continue 로 두고 $LASTEXITCODE 로만 판단한다.
$ErrorActionPreference = 'Continue'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Test-Path $FcmJsonPath)) {
    Write-Error "Firebase JSON 을 찾을 수 없습니다: $FcmJsonPath"
    exit 1
}

function Invoke-MysqlSql {
    param(
        [Parameter(Mandatory=$true)][string]$Sql,
        [string]$Label = "sql"
    )
    # PS 5.1 의 `$str | docker exec -i ...` 는 stdin 변환 중 CRLF/LF 가 string literal 안에
    # 섞여 들어가는 경우가 있다 (특히 FCM JSON 같은 긴 값이 single quote 안에 들어갈 때
    # credential_data 안에 raw LF 가 저장되어 ChannelCredentialService 파싱 실패).
    # 그래서 BOM 없는 UTF-8 로 임시 파일을 만들고 컨테이너로 cp 후 `mysql ... -e "source ..."`
    # 로 적용한다.
    $tmpName = "loadtest-$Label-" + [Guid]::NewGuid().ToString('N').Substring(0,8) + ".sql"
    $tmpLocal = Join-Path $env:TEMP $tmpName
    [System.IO.File]::WriteAllText($tmpLocal, $Sql, [System.Text.UTF8Encoding]::new($false))
    docker cp $tmpLocal "${MysqlContainer}:/tmp/$tmpName" | Out-Null
    $out = docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql "-u$MysqlUser" --default-character-set=utf8mb4 $MysqlDb -e "source /tmp/$tmpName" 2>&1
    if ($out) { $out | ForEach-Object { Write-Host $_ } }
    docker exec $MysqlContainer rm -f "/tmp/$tmpName" | Out-Null
    Remove-Item $tmpLocal -ErrorAction SilentlyContinue
}

# ─── 1. staging bootstrap (OWNER + Project + FCM credential)
Write-Host "[1/4] staging-bootstrap (OWNER + Project + FCM credential)"
# 주의: ConvertFrom-Json | ConvertTo-Json -Compress 는 private_key 의 escape `\n` 을 실제 LF 로
#       unescape 해버려, 결과 JSON 에 raw newline 이 들어간다. (channel_credentials 저장 후
#       worker 의 DeliveryKafkaConsumer 가 JSON 파싱 시 "Illegal unquoted character (LF)" 로 실패.)
#       원본 파일에는 raw newline 이 없으므로(Firebase 표준은 escape \n) 줄바꿈만 제거하면 충분.
$fcmJsonInline = (Get-Content -Raw -Path $FcmJsonPath) -replace "`r", "" -replace "`n", ""
# MySQL string literal 의 backslash escape (\n → LF) 때문에 JSON 의 `\n` (literal 2글자) 이
# 저장 시점에 LF 로 변환되어 워커의 JSON 파서가 실패한다. backslash 를 한번 더 escape (`\\`)
# 하고 single quote 도 SQL escape (`''`).
$fcmSqlEscaped = $fcmJsonInline.Replace('\', '\\').Replace("'", "''")
$tmpl = Get-Content -Raw -Path (Join-Path $scriptDir "00-staging-bootstrap.sql.template")
$bootstrapSql = $tmpl.Replace("__REPLACE_WITH_SERVICE_ACCOUNT_JSON__", $fcmSqlEscaped)
Invoke-MysqlSql -Sql $bootstrapSql -Label "bootstrap"

# ─── 2. loadtest 01 users + devices
function Render-Sql {
    param([string]$File)
    $raw = Get-Content -Raw -Path (Join-Path $scriptDir $File)
    return $raw.Replace('${PROJECT_ID}', "$ProjectId").Replace('${MEMBER_ID}', "$MemberId")
}

Write-Host "[2/4] 01-loadtest-users-devices.sql"
Invoke-MysqlSql -Sql (Render-Sql "01-loadtest-users-devices.sql") -Label "01-users"

Write-Host "[3/4] 02-loadtest-segment.sql"
Invoke-MysqlSql -Sql (Render-Sql "02-loadtest-segment.sql") -Label "02-segment"

Write-Host "[4/4] 03-loadtest-campaign.sql"
Invoke-MysqlSql -Sql (Render-Sql "03-loadtest-campaign.sql") -Label "03-campaign"

Write-Host ""
Write-Host "Staging 시드 완료."
Write-Host "  - project_id = $ProjectId, member_id = $MemberId"
Write-Host "  - 03 의 출력에서 campaign_id 확인 후 trigger-campaign-local.ps1 에 인자로 전달"

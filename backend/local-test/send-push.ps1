param(
    [Parameter(Mandatory=$true)][string]$Token,
    [string]$Title = "Butterfly 테스트 푸시",
    [string]$Body  = "백엔드에서 보낸 알림입니다",
    [string]$ImageUrl = $null,
    [string]$ApiBase = "http://localhost:8080",
    [int]$ProjectId = 1,
    [string]$KeyPath = "C:\Users\SSAFY\Downloads\butterfly-test-ce57c-firebase-adminsdk-fbsvc-6f6aaa8981.json"
)

if (-not (Test-Path $KeyPath)) {
    Write-Error "Service account key not found at: $KeyPath"
    exit 1
}

$credentialJson = Get-Content $KeyPath -Raw

$payload = [ordered]@{
    credentialJson = $credentialJson
    tokens         = @($Token)
    title          = $Title
    body           = $Body
}
if ($ImageUrl) { $payload.imageUrl = $ImageUrl }

$json = $payload | ConvertTo-Json -Depth 5 -Compress

Write-Host "POST $ApiBase/api/projects/$ProjectId/fcm/send"
Write-Host "  title: $Title"
Write-Host "  token: $($Token.Substring(0, [Math]::Min(20, $Token.Length)))..."
Write-Host ""

$response = Invoke-RestMethod `
    -Uri "$ApiBase/api/projects/$ProjectId/fcm/send" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $json

$response | ConvertTo-Json -Depth 6

$ErrorActionPreference = "Stop"

function Post-Json {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)]$Body
    )

    return Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
}

$base = "http://localhost:8080"
$pool = "orderThreadPool"
$version = [Int64](Get-Date -UFormat %s)
$version = $version * 1000

Write-Host "Step 1/6: Start stress traffic"
Invoke-RestMethod -Uri "$base/demo/stress/start" -Method Post
Start-Sleep -Seconds 5

Write-Host "Step 2/6: Check stress stats"
$stats1 = Invoke-RestMethod -Uri "$base/demo/stress/stats" -Method Get
Write-Host $stats1

Write-Host "Step 3/6: Publish a refresh command"
$refreshBody = @{
    requestId = "manual-$version"
    version = $version
    poolName = $pool
    source = "ops-script"
    reason = "dynamic-tp-demo-scale-up"
    coreSize = 10
    maxSize = 20
    queueCapacity = 1000
    keepAliveSeconds = 60
    timestamp = [Int64](Get-Date -UFormat %s) * 1000
}
$postResult = Post-Json -Url "$base/demo/refresh" -Body $refreshBody
Write-Host $postResult
Start-Sleep -Seconds 3

Write-Host "Step 4/6: Query current runtime state"
$runtime = Invoke-RestMethod -Uri "$base/actuator/dynamicThreadPools" -Method Get
$runtime | ConvertTo-Json -Depth 8

Write-Host "Step 5/6: Read version history"
$history = Invoke-RestMethod -Uri "$base/demo/config/history/$pool?limit=10" -Method Get
$history | ConvertTo-Json -Depth 8

Write-Host "Step 6/6: Roll back to just-applied version (safe demo)"
$rollbackVersion = $version + 1
$rollbackBody = @{
    requestId = "rollback-$rollbackVersion"
    version = $rollbackVersion
    poolName = $pool
    targetVersion = $version
    source = "ops-script"
    reason = "dynamic-tp-demo-rollback"
    timestamp = [Int64](Get-Date -UFormat %s) * 1000
}
$rollbackResult = Post-Json -Url "$base/demo/rollback" -Body $rollbackBody
Write-Host $rollbackResult

Write-Host "Demo finished."
Write-Host "Optional cleanup: docker compose -f .\docker-compose.yml down"

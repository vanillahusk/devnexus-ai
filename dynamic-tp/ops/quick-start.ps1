$ErrorActionPreference = "Stop"

function Get-ComposeCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "Docker is not installed or not in PATH."
    }
    return "docker compose"
}

function Wait-Http {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $false)][int]$TimeoutSeconds = 180
    )

    $start = Get-Date
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "Ready: $Url"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Timeout waiting for $Url"
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$compose = Get-ComposeCommand
Write-Host "Starting demo stack with: $compose up -d --build"
& docker compose up -d --build

Write-Host "Waiting for application and observability endpoints..."
Wait-Http -Url "http://localhost:8080/actuator/health"
Wait-Http -Url "http://localhost:9090/-/ready"
Wait-Http -Url "http://localhost:3000/login"

Write-Host ""
Write-Host "All services are up:"
Write-Host "- Demo App:    http://localhost:8080"
Write-Host "- Prometheus:  http://localhost:9090"
Write-Host "- Grafana:     http://localhost:3000 (admin/admin)"
Write-Host ""
Write-Host "Next step: run .\demo-flow.ps1 for an end-to-end dynamic thread pool demo."

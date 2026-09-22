<#
.SYNOPSIS
  Ejecuta un script k6 de performance usando la imagen Docker grafana/k6.

.DESCRIPTION
  Monta la carpeta perf/ en /perf dentro del contenedor y ejecuta el script
  indicado. Por defecto apunta a la API local via host.docker.internal:8080.

.EXAMPLE
  .\perf\run.ps1 scenarios\smoke.test.js
  .\perf\run.ps1 scenarios\load.test.js -SaveJson
  .\perf\run.ps1 endpoints\booking.test.js -BaseUrl http://192.168.1.20:8080 -ExtraEnv "BROWSER_VUS=10,HOLD_DURATION=3m"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Script,

    [string]$BaseUrl = "http://host.docker.internal:8080",

    [string]$Password = "Demo1234!",

    [switch]$SaveJson,

    [string]$ExtraEnv = ""
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$output = Join-Path $root "output"
New-Item -ItemType Directory -Force -Path $output | Out-Null

if ($ExtraEnv) { $envList = $ExtraEnv.Split(',') } else { $envList = @() }

$dockerArgs = @(
    "run", "--rm",
    "-v", "${root}:/perf",
    "--add-host", "host.docker.internal:host-gateway",
    "-e", "BASE_URL=$BaseUrl",
    "-e", "PASSWORD=$Password"
)

foreach ($e in $envList) {
    $e = $e.Trim()
    if ($e -eq "") { continue }
    if ($e -notmatch '=') { Write-Error "ExtraEnv debe tener formato KEY=value[,KEY=value...]. Recibido: '$e'"; exit 1 }
    $dockerArgs += @("-e", $e)
}

$dockerArgs += @("grafana/k6", "run")

if ($SaveJson) {
    $reportName = [IO.Path]::GetFileNameWithoutExtension($Script) + "-" + (Get-Date -Format "yyyyMMddHHmmss") + ".json"
    $dockerArgs += "--summary-export=/perf/output/$reportName"
    Write-Host "Reporte JSON en perf\output\$reportName" -ForegroundColor Yellow
}

$containerPath = "/perf/$($Script -replace '[\\/]+', '/')"
$containerPath = $containerPath -replace '/+', '/'
$dockerArgs += $containerPath

Write-Host "==> docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
& docker @dockerArgs
exit $LASTEXITCODE
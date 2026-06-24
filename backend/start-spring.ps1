$ErrorActionPreference = "Stop"

$goal = "spring-boot:run"
if ($args.Count -gt 0) {
  $goal = $args[0]
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoDir = Split-Path -Parent $scriptDir
$localMaven = Join-Path $repoDir ".tools\apache-maven-3.9.16\bin\mvn.cmd"
$envPath = Join-Path $repoDir ".env"

function Import-DotEnv {
  if (!(Test-Path $envPath)) {
    return
  }

  Get-Content -LiteralPath $envPath | ForEach-Object {
    $line = $_.Trim()
    if (!$line -or $line.StartsWith("#") -or $line -notmatch "^\s*([^=]+?)\s*=\s*(.*)\s*$") {
      return
    }

    $name = $Matches[1].Trim()
    $value = $Matches[2].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

function Get-ConfiguredBackendPort {
  if ($env:PORT) {
    return $env:PORT
  }

  if (Test-Path $envPath) {
    $portLine = Get-Content -LiteralPath $envPath |
      Where-Object { $_ -match '^\s*PORT\s*=' } |
      Select-Object -First 1

    if ($portLine) {
      return ($portLine -replace '^\s*PORT\s*=\s*', '').Trim()
    }
  }

  return "8080"
}

function Get-PortListenerProcessId($port) {
  $listener = netstat -ano |
    Select-String "[:.]$port\s+.*LISTENING\s+(\d+)" |
    Select-Object -First 1

  if ($listener -and $listener.Matches[0].Groups.Count -gt 1) {
    return $listener.Matches[0].Groups[1].Value
  }

  return ""
}

Import-DotEnv

if ($goal -eq "spring-boot:run") {
  $backendPort = Get-ConfiguredBackendPort
  $listenerProcessId = Get-PortListenerProcessId $backendPort

  if ($listenerProcessId) {
    $processName = "unknown"
    try {
      $processName = (Get-Process -Id $listenerProcessId -ErrorAction Stop).ProcessName
    } catch {
      $processName = "unknown"
    }

    Write-Host "Backend port $backendPort is already in use by PID $listenerProcessId ($processName)." -ForegroundColor Yellow
    Write-Host "Stop that process or change PORT in .env before starting the backend again." -ForegroundColor Yellow
    exit 1
  }
}

Push-Location $scriptDir
try {
  if (Test-Path $localMaven) {
    & $localMaven $goal
    exit $LASTEXITCODE
  }

  & mvn $goal
  exit $LASTEXITCODE
} finally {
  Pop-Location
}

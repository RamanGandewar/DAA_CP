$ErrorActionPreference = "Stop"

if (!(Test-Path out)) {
  New-Item -ItemType Directory -Path out | Out-Null
}

$files = Get-ChildItem -Recurse -File src\*.java | ForEach-Object { $_.FullName }
if ($files.Count -eq 0) {
  throw "No Java files found under src"
}

javac -d out $files

function Test-PortAvailable([int]$Port) {
  $listener = $null
  try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
    return $true
  } catch {
    return $false
  } finally {
    if ($listener -ne $null) {
      $listener.Stop()
    }
  }
}

$startPort = 8080
$maxChecks = 20
$selectedPort = $null

for ($i = 0; $i -lt $maxChecks; $i++) {
  $candidate = $startPort + $i
  if (Test-PortAvailable $candidate) {
    $selectedPort = $candidate
    break
  }
}

if ($selectedPort -eq $null) {
  throw "No free port found in range $startPort-$($startPort + $maxChecks - 1)"
}

Write-Host "Starting server on http://localhost:$selectedPort"
java -cp out Main $selectedPort

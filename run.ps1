$ErrorActionPreference = "Stop"

if (!(Test-Path out)) {
  New-Item -ItemType Directory -Path out | Out-Null
}

$files = Get-ChildItem -Recurse -File src\*.java | ForEach-Object { $_.FullName }
if ($files.Count -eq 0) {
  throw "No Java files found under src"
}

$libClasspath = ""
if (Test-Path lib) {
  $jarFiles = Get-ChildItem -Path lib -Filter *.jar -File | ForEach-Object { $_.FullName }
  if ($jarFiles.Count -gt 0) {
    $libClasspath = ($jarFiles -join ';')
  }
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
Write-Host "Dataset mode is selected from the UI (CSV or XLSX)."
if ($libClasspath) {
  java --enable-native-access=ALL-UNNAMED -cp "out;$libClasspath" Main $selectedPort
} else {
  java -cp out Main $selectedPort
}

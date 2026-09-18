# Run Maven tests inside generated-app (after pipeline)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$App = Join-Path $Root "generated-app"

if (Test-Path "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe") {
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}
if (Test-Path "C:\Users\91984\Tools\apache-maven-3.9.9\bin\mvn.cmd") {
    $env:Path = "C:\Users\91984\Tools\apache-maven-3.9.9\bin;" + $env:Path
}

if (-not (Test-Path (Join-Path $App "pom.xml"))) {
    throw "generated-app not found. Run .\scripts\run-pipeline.ps1 first."
}

Set-Location $App
Write-Host "Running unit + functional tests in generated-app..." -ForegroundColor Cyan
mvn test

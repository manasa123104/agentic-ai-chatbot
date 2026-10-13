# Start BOT on http://localhost:8080

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (Test-Path "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe") {
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}
if (Test-Path "C:\Users\91984\Tools\apache-maven-3.9.9\bin\mvn.cmd") {
    $env:Path = "C:\Users\91984\Tools\apache-maven-3.9.9\bin;" + $env:Path
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java not found. Install JDK 17+ and add it to PATH."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn not found. Install Maven and add it to PATH."
}

Write-Host "Starting BOT on http://localhost:8080 ..." -ForegroundColor Cyan
Write-Host "Code converter: http://localhost:8080/convert" -ForegroundColor Cyan
if ($env:OPENAI_API_KEY) {
    Write-Host "OPENAI_API_KEY detected — LLM mode enabled." -ForegroundColor Green
} elseif ($env:OPENAI_BASE_URL -match "localhost|127\.0\.0\.1") {
    Write-Host "Local LLM endpoint $env:OPENAI_BASE_URL — LLM mode enabled." -ForegroundColor Green
} else {
    Write-Host "No OPENAI_API_KEY — offline Java template mode for websites." -ForegroundColor Yellow
}
try {
    Invoke-RestMethod "http://localhost:11434/api/tags" -TimeoutSec 2 | Out-Null
    Write-Host "Ollama detected — the code converter will use your local coding model for any code." -ForegroundColor Green
} catch {
    Write-Host "Ollama not running — the code converter handles built-in samples only (start Ollama to convert any code)." -ForegroundColor Yellow
}

mvn spring-boot:run

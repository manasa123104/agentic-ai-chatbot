# Start the Agentic AI Chatbot web UI
# http://localhost:8080

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

Write-Host "Starting Agentic AI Chatbot on http://localhost:8080 ..." -ForegroundColor Cyan
if ($env:OPENAI_API_KEY) {
    Write-Host "OPENAI_API_KEY detected — LLM mode enabled." -ForegroundColor Green
} else {
    Write-Host "No OPENAI_API_KEY — offline Java template mode." -ForegroundColor Yellow
}

mvn spring-boot:run

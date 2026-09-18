# Agentic AI Chatbot pipeline (Windows PowerShell)
# Requires: JDK 17+, Maven
#
# Usage:
#   .\scripts\run-pipeline.ps1 -UserStory "As a user, I want a login page so I can access my dashboard."
#   .\scripts\run-pipeline.ps1 -UserStory "..." -SkipTests
#   .\scripts\run-web.ps1

param(
    [Parameter(Mandatory = $true)]
    [string]$UserStory,

    [switch]$SkipUnit,
    [switch]$SkipFunctional,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

# Prefer known local installs if PATH is incomplete
$candidatesJava = @(
    $env:JAVA_HOME,
    "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot",
    "C:\Program Files\Microsoft\jdk-17*"
)
foreach ($c in $candidatesJava) {
    if ($c -and (Test-Path "$c\bin\java.exe")) {
        $env:JAVA_HOME = (Resolve-Path $c).Path
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
        break
    }
}
$mavenHome = "C:\Users\91984\Tools\apache-maven-3.9.9"
if (Test-Path "$mavenHome\bin\mvn.cmd") {
    $env:Path = "$mavenHome\bin;" + $env:Path
}

function Require-Command($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $name. Install JDK 17+ and Maven, then reopen the terminal."
    }
}

Require-Command "java"
Require-Command "mvn"

Write-Host "==> Building agentic-ai-chatbot..." -ForegroundColor Cyan
mvn -q -DskipTests package

$jar = Get-ChildItem "$Root\target\agentic-ai-chatbot-*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Select-Object -First 1

if (-not $jar) {
    throw "Jar not found under target/. Build failed?"
}

$argsList = @(
    "-jar", $jar.FullName,
    "--story=$UserStory",
    "--exit"
)

if ($SkipTests -or $SkipUnit) { $argsList += "--skip-unit" }
if ($SkipTests -or $SkipFunctional) { $argsList += "--skip-functional" }

Write-Host "==> Running 3-bot pipeline..." -ForegroundColor Cyan
Write-Host "Story: $UserStory"
& java @argsList

if ($LASTEXITCODE -ne 0) {
    throw "Pipeline failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "==> Generated app is in: $Root\generated-app" -ForegroundColor Green
Write-Host "    cd generated-app"
Write-Host "    mvn spring-boot:run    # app on http://localhost:8090"
Write-Host "    mvn test               # run unit + functional tests"

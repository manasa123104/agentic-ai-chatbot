<#
  Starts BOT in public-safe mode (if it is not already running in that mode) and a free Cloudflare
  "quick tunnel", then prints the public https://*.trycloudflare.com URL.

  Usage (PowerShell):   .\scripts\start-public.ps1            # reuse a running BOT / tunnel
                        .\scripts\start-public.ps1 -Restart   # stop and restart both (new URL)
  Stop the tunnel:      Get-Process cloudflared | Stop-Process

  Requirements: a built jar (mvn -q clean package), Java 17 (JAVA_HOME or PATH) and cloudflared
  (PATH, the CLOUDFLARED environment variable, or -Cloudflared <path>).

  Public-safe mode: "Folder on this computer" input is disabled, the owner's GitHub/OpenAI secrets and Git
  logins are hidden from the process, and every visitor only sees their own jobs.
  A quick-tunnel URL changes every time the tunnel starts. The laptop must stay on for the link to work.
#>
param(
    [int]$Port = 8080,
    [string]$Model = $(if ($env:CONVERTER_MODEL) { $env:CONVERTER_MODEL } else { 'qwen2.5-coder:1.5b' }),
    [string]$Cloudflared = $env:CLOUDFLARED,
    [string[]]$ExtraLogDir = @(),   # other folders that hold cloudflared logs (*.log) from earlier launches
    [switch]$Restart
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$jar = Join-Path $root 'target\agentic-ai-chatbot-1.0.0.jar'

$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' }
        else { (Get-Command java -ErrorAction Stop).Source }
if (-not $Cloudflared) {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { $Cloudflared = $cmd.Source }
}
if (-not $Cloudflared -or -not (Test-Path $Cloudflared)) {
    throw 'cloudflared not found. Install it, add it to PATH, or pass -Cloudflared <path> (or set CLOUDFLARED).'
}

function Get-BotPid {
    $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { $c.OwningProcess } else { $null }
}
function Test-BotSafe {
    try {
        $html = (Invoke-WebRequest "http://localhost:$Port/convert" -UseBasicParsing -TimeoutSec 20).Content
        return ($html -notmatch 'Folder on this computer')
    } catch { return $false }
}
function Test-Url($u) {
    # A quick tunnel is live only if the public URL actually reaches BOT.
    $code = & curl.exe -s -o NUL -w '%{http_code}' --max-time 10 "$u/convert" 2>$null
    return ($code -eq '200')
}
function Find-LiveTunnelUrl {
    # Newest log first; the live tunnel is the first logged URL that answers.
    $dirs = @($logs) + $ExtraLogDir | Where-Object { $_ -and (Test-Path $_) }
    $files = $dirs | ForEach-Object { Get-ChildItem $_ -Filter '*.log' -File -ErrorAction SilentlyContinue } |
        Sort-Object LastWriteTime -Descending
    $seen = @{}
    foreach ($file in $files) {
        $urls = Select-String -Path $file.FullName -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Matches[0].Value } | Select-Object -Unique
        foreach ($u in $urls) {
            if (-not $seen[$u]) { $seen[$u] = $true; if (Test-Url $u) { return $u } }
        }
    }
    return $null
}
if ($Restart) {
    Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
    $p = Get-BotPid; if ($p) { Stop-Process -Id $p -Force; Start-Sleep 3 }
}

# 1. BOT (public-safe)
$botPid = Get-BotPid
if ($botPid -and -not (Test-BotSafe)) {
    Write-Host 'BOT is running with local folders enabled - restarting it in public-safe mode...'
    Stop-Process -Id $botPid -Force; Start-Sleep 3; $botPid = $null
}
if (-not $botPid) {
    if (-not (Test-Path $jar)) { throw "Build first: mvn -q clean package  (missing $jar)" }
    foreach ($v in 'GITHUB_TOKEN', 'GH_TOKEN', 'OPENAI_API_KEY', 'OPENAI_BASE_URL', 'GOOGLE_CLIENT_SECRET') {
        Remove-Item "Env:$v" -ErrorAction SilentlyContinue
    }
    $env:GIT_CONFIG_NOSYSTEM = '1'; $env:GIT_CONFIG_GLOBAL = 'NUL'
    $env:GCM_INTERACTIVE = 'never'; $env:GIT_TERMINAL_PROMPT = '0'
    $env:CONVERTER_MODEL = $Model
    $env:CONVERTER_ALLOW_LOCAL_PATHS = 'false'
    $env:PORT = "$Port"
    Write-Host 'Starting BOT ...'
    Start-Process -FilePath $java -WorkingDirectory $root -WindowStyle Hidden `
        -ArgumentList '-jar', $jar, '--agentic.converter.allow-local-paths=false' `
        -RedirectStandardOutput (Join-Path $logs 'bot.out.log') -RedirectStandardError (Join-Path $logs 'bot.err.log')
    for ($i = 0; $i -lt 60 -and -not (Get-BotPid); $i++) { Start-Sleep 2 }
    if (-not (Get-BotPid)) { throw "BOT did not start - see $logs\bot.err.log" }
}
Write-Host "BOT is listening on http://localhost:$Port (public-safe mode)."

# 2. Tunnel (reuse a running one so the URL stays the same)
$tunnelLog = Join-Path $logs 'tunnel.log'
$url = $null
$running = Get-Process cloudflared -ErrorAction SilentlyContinue
if ($running) { $url = Find-LiveTunnelUrl }
if (-not $url) {
    if ($running) { $running | Stop-Process -Force; Start-Sleep 2 }
    Remove-Item $tunnelLog, "$tunnelLog.out" -ErrorAction SilentlyContinue
    # cloudflared writes its log (including the URL) to stderr
    Start-Process -FilePath $Cloudflared -WindowStyle Hidden `
        -ArgumentList 'tunnel', '--no-autoupdate', '--url', "http://localhost:$Port" `
        -RedirectStandardError $tunnelLog -RedirectStandardOutput "$tunnelLog.out"
    $logged = $null
    for ($i = 0; $i -lt 30 -and -not $logged; $i++) {
        Start-Sleep 2
        $m = Select-String -Path $tunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue | Select-Object -Last 1
        if ($m) { $logged = $m.Matches[0].Value }
    }
    # DNS for a brand-new tunnel can take a few seconds
    for ($i = 0; $logged -and $i -lt 15 -and -not $url; $i++) { if (Test-Url $logged) { $url = $logged } else { Start-Sleep 3 } }
}
if (-not $url) { throw "No working tunnel URL found - see $logs\tunnel.log" }
Write-Host ''
Write-Host "Public URL:     $url" -ForegroundColor Green
Write-Host "Code converter: $url/convert"
Write-Host '(The URL changes whenever the tunnel restarts. Keep this laptop on.)'

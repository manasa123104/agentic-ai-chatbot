<#
  Deploys BOT to a public Hugging Face Space (Docker SDK) at https://huggingface.co/spaces/<user>/bot.

  Usage (PowerShell):
      $env:HF_TOKEN = 'hf_...'            # or store it once:  [Environment]::SetEnvironmentVariable('HF_TOKEN','hf_...','User')
      .\scripts\deploy-hf-space.ps1               # create the Space if missing, push the files, print the public URL
      .\scripts\deploy-hf-space.ps1 -DryRun       # only build the staging folder and check it (no token, no network)

  What it does
    1. Reads the token from $env:HF_TOKEN, else from the user-level HF_TOKEN variable. The token is never printed
       and never written to disk or to git config.
    2. GET https://huggingface.co/api/whoami-v2 (curl.exe) to find the user name.
    3. Creates the Space <user>/bot (sdk docker, public) if it does not exist yet.
    4. Builds a temp staging folder laid out as the Space repo root:
         Dockerfile, start.sh, README.md  (from deploy\hf-space\)   +   pom.xml, src\main\  (the app source)
       then pushes it with git over https. The token is passed with `git -c http.extraHeader=...` for that one
       command only; no git config, credential helper or remote is touched (the staging repo is thrown away).
    5. Prints https://<user>-bot.hf.space. The Space then builds the image from source (see docs/HOSTING.md).

  The token needs WRITE access (a classic "write" token, or a fine-grained token that may create and write Spaces).
  The Space repo history is replaced by a single commit every run (force push), which is intended: the Space
  contains nothing but this generated layout.
#>
[CmdletBinding()]
param(
    [string]$SpaceName = 'bot',
    [string]$Owner = '',       # leave empty to use your own account; set an organisation name to deploy there
    [switch]$DryRun            # stage and validate only
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$hfDir = Join-Path $root 'deploy\hf-space'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$script:Token = ''

function Write-Step([string]$text) { Write-Host "==> $text" -ForegroundColor Cyan }

function Get-HfToken {
    $t = $env:HF_TOKEN
    if ([string]::IsNullOrWhiteSpace($t)) { $t = [Environment]::GetEnvironmentVariable('HF_TOKEN', 'User') }
    if ([string]::IsNullOrWhiteSpace($t)) {
        throw "No Hugging Face token. Set it with:  `$env:HF_TOKEN = 'hf_...'   (create a WRITE token at https://huggingface.co/settings/tokens)"
    }
    return $t.Trim()
}

# Replaces the token in any text before it is shown, as a safety net.
function Hide-Token([string]$text) {
    if ([string]::IsNullOrEmpty($text) -or [string]::IsNullOrEmpty($script:Token)) { return $text }
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("x:$($script:Token)"))
    return $text.Replace($script:Token, '***').Replace($basic, '***')
}

# curl.exe call with the Authorization header supplied through a config read from stdin (-K -), so the token does
# not appear on any command line. Returns @{ Status = <http code>; Body = <text> }.
function Invoke-HfApi([string]$Method, [string]$Url, [string]$JsonBody = '') {
    $outFile = [IO.Path]::GetTempFileName()
    $bodyFile = $null
    try {
        $curlArgs = @('-sS', '-K', '-', '-X', $Method, '-o', $outFile, '-w', '%{http_code}', '-H', 'Accept: application/json')
        if ($JsonBody) {
            $bodyFile = [IO.Path]::GetTempFileName()
            [IO.File]::WriteAllText($bodyFile, $JsonBody, $utf8NoBom)
            $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$bodyFile")
        }
        $curlArgs += $Url
        $config = "header = `"Authorization: Bearer $($script:Token)`"`n"
        $status = $config | & curl.exe @curlArgs
        if ($LASTEXITCODE -ne 0) { throw "curl.exe failed (exit $LASTEXITCODE) calling $Url" }
        $text = [IO.File]::ReadAllText($outFile)
        return @{ Status = [int]$status; Body = $text }
    }
    finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $outFile
        if ($bodyFile) { Remove-Item -Force -ErrorAction SilentlyContinue $bodyFile }
    }
}

# Runs git (native stderr must not abort the script); returns @{ Code; Output } with the token masked.
function Invoke-Git([string[]]$GitArgs, [string]$WorkDir) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    Push-Location $WorkDir
    try {
        $output = & git @GitArgs 2>&1 | ForEach-Object { "$_" }
        $code = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $ErrorActionPreference = $previous
    }
    return @{ Code = $code; Output = (Hide-Token (($output -join "`n"))) }
}

function Copy-Normalized([string]$From, [string]$To) {
    # LF line endings, UTF-8 without BOM (start.sh must not contain CR, and HF reads README.md front-matter first)
    $text = [IO.File]::ReadAllText($From).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($To, $text, $utf8NoBom)
}

function New-Staging {
    $stage = Join-Path ([IO.Path]::GetTempPath()) ("bot-hf-space-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Path $stage | Out-Null
    foreach ($name in 'Dockerfile', 'start.sh', 'README.md') {
        $src = Join-Path $hfDir $name
        if (-not (Test-Path $src)) { throw "Missing $src" }
        Copy-Normalized $src (Join-Path $stage $name)
    }
    foreach ($needed in @('pom.xml', 'src\main\java', 'src\main\resources')) {
        if (-not (Test-Path (Join-Path $root $needed))) { throw "Missing $needed in $root" }
    }
    Copy-Item (Join-Path $root 'pom.xml') (Join-Path $stage 'pom.xml')
    New-Item -ItemType Directory -Path (Join-Path $stage 'src') | Out-Null
    Copy-Item (Join-Path $root 'src\main') (Join-Path $stage 'src\main') -Recurse   # tests are not needed to build the jar
    [IO.File]::WriteAllText((Join-Path $stage '.gitattributes'), "* text=auto eol=lf`n*.sh text eol=lf`n", $utf8NoBom)

    # Sanity checks on what will be pushed
    $readme = [IO.File]::ReadAllText((Join-Path $stage 'README.md'))
    if (-not $readme.StartsWith("---`n")) { throw 'README.md must start with the Hugging Face front-matter (---).' }
    foreach ($key in 'title: BOT', 'sdk: docker', 'app_port: 7860', 'pinned: false') {
        if ($readme -notmatch [regex]::Escape($key)) { throw "README.md front-matter is missing '$key'" }
    }
    if ([IO.File]::ReadAllText((Join-Path $stage 'start.sh')).Contains("`r")) { throw 'start.sh still contains CR characters.' }
    foreach ($flag in '--server.port=7860', '--agentic.converter.allow-local-paths=false', '--agentic.public-mode=true') {
        if ([IO.File]::ReadAllText((Join-Path $stage 'start.sh')) -notmatch [regex]::Escape($flag)) { throw "start.sh is missing $flag" }
    }
    return $stage
}

function Get-SpaceHost([string]$user, [string]$name) {
    # Hugging Face lower-cases the name and turns "_" and "." into "-" in the *.hf.space sub-domain.
    $sub = ("$user-$name").ToLowerInvariant() -replace '[._]', '-'
    return "https://$sub.hf.space"
}

# ---------------------------------------------------------------------------------------------------------------
$stage = $null
try {
    Write-Step 'Preparing the Space files in a temp staging folder'
    $stage = New-Staging
    $count = (Get-ChildItem -Recurse -File $stage).Count
    Write-Host "    staged $count files in $stage (Dockerfile, start.sh, README.md, pom.xml, src/main)"

    if ($DryRun) {
        Write-Host ''
        Write-Host 'Dry run OK: the staging folder is valid. Nothing was sent to Hugging Face.' -ForegroundColor Green
        return
    }

    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe was not found on PATH.' }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git was not found on PATH.' }
    $script:Token = Get-HfToken

    Write-Step 'Checking the token (GET /api/whoami-v2)'
    $who = Invoke-HfApi 'GET' 'https://huggingface.co/api/whoami-v2'
    if ($who.Status -ne 200) {
        throw "Hugging Face rejected the token (HTTP $($who.Status)). Create a new WRITE token at https://huggingface.co/settings/tokens"
    }
    $user = ($who.Body | ConvertFrom-Json).name
    if ([string]::IsNullOrWhiteSpace($user)) { throw 'whoami-v2 returned no user name.' }
    $space = if ([string]::IsNullOrWhiteSpace($Owner)) { $user } else { $Owner.Trim() }
    Write-Host "    signed in as $user; Space: $space/$SpaceName"

    Write-Step "Looking for the Space $space/$SpaceName"
    $exists = Invoke-HfApi 'GET' "https://huggingface.co/api/spaces/$space/$SpaceName"
    if ($exists.Status -eq 200) {
        Write-Host '    it already exists'
    }
    elseif ($exists.Status -eq 404) {
        Write-Host '    not found, creating it (public, Docker SDK)'
        $payload = [ordered]@{ type = 'space'; name = $SpaceName; sdk = 'docker'; private = $false }
        if ($space -ne $user) { $payload['organization'] = $space }
        $created = Invoke-HfApi 'POST' 'https://huggingface.co/api/repos/create' ($payload | ConvertTo-Json -Compress)
        if ($created.Status -ne 200 -and $created.Status -ne 409) {
            throw "Could not create the Space (HTTP $($created.Status)): $(Hide-Token $created.Body)"
        }
    }
    else {
        throw "Unexpected answer while looking for the Space (HTTP $($exists.Status)): $(Hide-Token $exists.Body)"
    }

    Write-Step 'Committing the staging folder (temporary repository, nothing is stored in your git config)'
    $env:GIT_TERMINAL_PROMPT = '0'
    $identity = @('-c', 'user.name=BOT deploy', '-c', 'user.email=bot-deploy@users.noreply.huggingface.co', '-c', 'core.autocrlf=false')
    foreach ($step in @(
            @('init', '-q', '-b', 'main'),
            @('add', '-A'),
            @($identity + @('commit', '-q', '-m', 'Deploy BOT from source')))) {
        $r = Invoke-Git $step $stage
        if ($r.Code -ne 0) { throw "git $($step[0]) failed: $($r.Output)" }
    }

    Write-Step "Pushing to https://huggingface.co/spaces/$space/$SpaceName (this can take a minute)"
    $remote = "https://huggingface.co/spaces/$space/$SpaceName"
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($user):$($script:Token)"))
    $pushed = $false
    $lastOutput = ''
    foreach ($auth in @("Authorization: Bearer $($script:Token)", "Authorization: Basic $basic")) {
        $r = Invoke-Git @('-c', "http.extraHeader=$auth", 'push', '--force', $remote, 'HEAD:main') $stage
        if ($r.Code -eq 0) { $pushed = $true; break }
        $lastOutput = $r.Output
        Write-Host '    push was refused with this auth style, trying the other one...' -ForegroundColor Yellow
    }
    if (-not $pushed) { throw "git push failed. Check that the token has write access to $space/$SpaceName.`n$lastOutput" }

    $url = Get-SpaceHost $space $SpaceName
    $runtime = Invoke-HfApi 'GET' "https://huggingface.co/api/spaces/$space/$SpaceName/runtime"
    if ($runtime.Status -eq 200) {
        try { Write-Host "    Space stage: $(($runtime.Body | ConvertFrom-Json).stage)" } catch { }
    }
    Write-Host ''
    Write-Host 'Pushed. Hugging Face is now building the image from source (first build: roughly 10-25 minutes).' -ForegroundColor Green
    Write-Host "Build logs / settings:  https://huggingface.co/spaces/$space/$SpaceName"
    Write-Host "Public URL (when the build turns Running):  $url" -ForegroundColor Green
    Write-Host "Converter page:  $url/convert"
}
finally {
    $script:Token = ''
    Remove-Item Env:\GIT_TERMINAL_PROMPT -ErrorAction SilentlyContinue
    if ($stage -and (Test-Path $stage)) { Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $stage }
}

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('lead','neoforge','parity','release')]
    [string]$Agent
)

$ErrorActionPreference = 'Stop'
$Root = 'C:\Users\Panda'
$Repo = Join-Path $Root 'weft'
$LogDir = Join-Path $Repo '.crew\memory\_session\logs'
# Ordered profile fallback chains. Provider death (expired tokens, 402/404/429,
# unsupported model) must never stall an agent: the launcher walks the chain.
#
# Provider state 2026-08-20, VERIFIED by scripts/crew/probe-routes.py (not just
# the catalog listing — the catalog advertises routes that do not answer):
#   HEALTHY  : codex (cx), claude (cc), xai-oauth (xao)
#   DEAD     : github (gh/github)  -> model-missing on every id
#              antigravity (aug)   -> upstream 502
#              grok-cli (gc)       -> stream timeout
#              tllm                -> 403 egress-IP blocked
#              deepseek (ds)       -> no-credentials  (quota reset pending)
#              opencode (oc)       -> unauthorized    (quota reset pending)
#              cc-claude-fable-5   -> rate-limited    (quota reset pending)
#
# Chains therefore use only probe-verified routes, and get depth by spanning
# BOTH provider and model within the three healthy pools. Re-run
# `python scripts/crew/probe-routes.py` after any OmniRoute change; quota-limited
# entries sit last so they resume automatically once they recover.
$definitions = @{
    lead = @{
        Worktree = 'weft-wt-lead'
        Prompt   = 'lead-commit-duty.md'
        Profiles = @(
            'cx-gpt-5-6-sol-high',          # codex  (verified)
            'cc-claude-sonnet-5',           # claude (verified)
            'xao-grok-4-5',                 # xai    (verified)
            'cx-gpt-5-6-terra',             # codex  alt model
            'cc-claude-sonnet-4-6',         # claude alt model
            'xao-grok-4-3',                 # xai    alt model
            'cc-claude-fable-5'             # recovers when quota resets
        )
    }
    neoforge = @{
        Worktree = 'weft-wt-neoforge'
        Prompt   = 'neoforge-commit-duty.md'
        Profiles = @(
            'cx-gpt-5-6-sol-high',
            'cc-claude-opus-5',
            'cx-gpt-5-6-terra',
            'cc-claude-opus-4-8',
            'xao-grok-4-5',
            'cx-gpt-5-6-luna',
            'cc-claude-fable-5'
        )
    }
    parity = @{
        Worktree = 'weft-wt-parity'
        Prompt   = 'parity-commit-duty.md'
        Profiles = @(
            'cx-gpt-5-6-sol-high',
            'cc-claude-sonnet-5',
            'cc-claude-opus-5',
            'xao-grok-4-20-0309-reasoning', # reasoning SKU for hazard analysis
            'cx-gpt-5-6-terra',
            'cc-claude-opus-4-7',
            'xao-grok-4-5'
        )
    }
    release = @{
        Worktree = 'weft-wt-release'
        Prompt   = 'release-commit-duty.md'
        Profiles = @(
            'cx-gpt-5-6-sol-high',
            'cc-claude-sonnet-5',
            'cx-gpt-5-5',
            'cc-claude-sonnet-4-6',
            'xao-grok-4-5',
            'cx-gpt-5-6-luna',
            'xao-grok-4-3'
        )
    }
}
$definition = $definitions[$Agent]
$worktree = Join-Path $Root $definition.Worktree
$prompt = Join-Path $Repo ".crew\memory\_session\prompts\$($definition.Prompt)"
if (-not (Test-Path $prompt)) { throw "Missing prompt: $prompt" }

$key = $env:OMNIROUTE_API_KEY
if (-not $key) { $key = [Environment]::GetEnvironmentVariable('OMNIROUTE_API_KEY', 'User') }
if (-not $key) { throw 'OMNIROUTE_API_KEY is not configured' }
$env:OMNIROUTE_API_KEY = $key

$session = Join-Path $worktree '.crew\memory\_session'
New-Item -ItemType Directory -Force -Path $session,$LogDir | Out-Null
$output = Join-Path $session 'watchdog-lastmsg.txt'
$log = Join-Path $LogDir "$Agent-watchdog-agent.log"
$errorLog = Join-Path $LogDir "$Agent-watchdog-agent.err.log"

function Quote-Arg([string]$value) {
    if ($value -match '[\s"]') {
        return '"' + ($value -replace '"', '\"') + '"'
    }
    return $value
}

# Resolve npm shim to node + codex.js so Windows PowerShell 5.1 ProcessStartInfo works.
$npmRoot = Split-Path (Get-Command codex).Source -Parent
$nodeExe = Join-Path $npmRoot 'node.exe'
$codexJs = Join-Path $npmRoot 'node_modules\@openai\codex\bin\codex.js'
if (-not (Test-Path $nodeExe)) { $nodeExe = (Get-Command node).Source }
if (-not (Test-Path $codexJs)) { throw "Missing Codex entrypoint: $codexJs" }

# Codex requires UTF-8 stdin. Rewrite prompt bytes as UTF-8 (no BOM) if needed.
$promptBytes = [IO.File]::ReadAllBytes($prompt)
$utf8Strict = New-Object System.Text.UTF8Encoding $false, $true
try {
    [void]$utf8Strict.GetString($promptBytes)
    $promptUtf8 = $promptBytes
} catch {
    $promptText = [IO.File]::ReadAllText($prompt)
    $promptUtf8 = [Text.Encoding]::UTF8.GetBytes($promptText)
}

# Provider-level failures that must trigger the next profile rather than
# stopping the agent. Expired DeepSeek tokens showed up as 402 then 404.
$failoverPatterns = @(
    '\b402\b',
    'Payment Required',
    'Insufficient Balance',
    'No active credentials',
    'invalid_api_key',
    'Unauthorized',
    'token (has )?expired',
    'expired token',
    'model .*not supported',
    'model .*not found',
    'Model metadata for .* not found',
    'exceeded retry limit'
)
$failoverRegex = [regex]::new(($failoverPatterns -join '|'), 'IgnoreCase')
$failoverLog = Join-Path $LogDir 'route-failover.log'

# Skip profiles whose OmniRoute config file no longer exists (renames happen
# after OmniRoute upgrades); otherwise each rename burns a failover attempt.
$codexHome = Join-Path $env:USERPROFILE '.codex'
$availableProfiles = @{}
Get-ChildItem $codexHome -Recurse -Filter '*.config.toml' -ErrorAction SilentlyContinue |
    ForEach-Object { $availableProfiles[$_.BaseName -replace '\.config$', ''] = $true }

$candidates = @()
foreach ($p in $definition.Profiles) {
    if ($availableProfiles.Count -eq 0 -or $availableProfiles.ContainsKey($p)) {
        $candidates += $p
    } else {
        Write-Host "SKIP missing profile: $p"
    }
}
if ($candidates.Count -eq 0) { $candidates = $definition.Profiles }

$finalCode = 1
$usedProfile = $null
$attempt = 0

foreach ($profile in $candidates) {
    $attempt++
    $argLine = @(
        (Quote-Arg $codexJs),
        'exec',
        '--profile', (Quote-Arg $profile),
        '-C', (Quote-Arg $worktree),
        '-s', 'workspace-write',
        '--dangerously-bypass-approvals-and-sandbox',
        '-o', (Quote-Arg $output),
        '--json',
        '-'
    ) -join ' '

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $nodeExe
    $startInfo.Arguments = $argLine
    $startInfo.WorkingDirectory = $worktree
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.StandardOutputEncoding = [Text.Encoding]::UTF8
    $startInfo.StandardErrorEncoding = [Text.Encoding]::UTF8

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $process.StandardInput.BaseStream.Write($promptUtf8, 0, $promptUtf8.Length)
    $process.StandardInput.BaseStream.Flush()
    $process.StandardInput.Close()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    "pid=$($process.Id) profile=$profile attempt=$attempt start=$(Get-Date -Format o)" |
        Set-Content (Join-Path $LogDir "$Agent-watchdog-agent.pid")

    $process.WaitForExit()
    $outText = $stdoutTask.Result
    $errText = $stderrTask.Result
    [IO.File]::AppendAllText($log, $outText)
    [IO.File]::AppendAllText($errorLog, $errText)

    $finalCode = $process.ExitCode
    $usedProfile = $profile

    if ($finalCode -eq 0 -and -not $failoverRegex.IsMatch($outText + $errText)) {
        break
    }

    if ($failoverRegex.IsMatch($outText + $errText)) {
        $note = "$(Get-Date -Format o) agent=$Agent profile=$profile exit=$finalCode ROUTE-FAILOVER"
        Add-Content -Path $failoverLog -Value $note
        Write-Host $note
        continue
    }

    # Genuine task failure on a healthy route: stop and surface it.
    break
}

"last_profile=$usedProfile exit=$finalCode end=$(Get-Date -Format o)" |
    Add-Content (Join-Path $LogDir "$Agent-watchdog-agent.pid")
exit $finalCode

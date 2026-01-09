param(
  [string]$EnvName = "dev",
  [Nullable[int]]$Port = $null,
  [string]$Token = $null,
  [string]$BaseUrl = $null,
  [string]$TestName = "Antigravity Agent",
  [switch]$NoAuth = $false,
  [int]$TimeoutSec = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
  Write-Error $Message
  exit 1
}

function Require([bool]$Condition, [string]$Message) {
  if (-not $Condition) { Fail $Message }
}

function Load-EnvFromHttpClientJson([string]$envName) {
  $envPath = Join-Path $PSScriptRoot "http-client.private.env.json"
  if (-not (Test-Path $envPath)) {
    return $null
  }

  $json = Get-Content $envPath -Raw | ConvertFrom-Json
  if ($null -eq $json.$envName) {
    return $null
  }
  return $json.$envName
}

function Post-Mcp([string]$url, [hashtable]$headers, [object]$bodyObj) {
  $body = ($bodyObj | ConvertTo-Json -Depth 64 -Compress)
  return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -Body $body -TimeoutSec $TimeoutSec
}

# Resolve config
$authEnabled = -not $NoAuth
if (-not $BaseUrl) {
  if ($null -eq $Port -or -not $Token) {
    $envConfig = Load-EnvFromHttpClientJson $EnvName
    if ($null -ne $envConfig) {
      if ($null -eq $Port) { $Port = [int]$envConfig.port }
      if (-not $Token) { $Token = [string]$envConfig.token }
      if ($envConfig.PSObject.Properties.Name -contains "auth") {
        $authEnabled = [bool]$envConfig.auth
      }
    }
  }

  Require ($null -ne $Port) "Port is required (pass -Port or configure requests/http-client.private.env.json env '$EnvName')"
  if ($authEnabled) {
    Require (-not [string]::IsNullOrWhiteSpace($Token)) "Token is required (pass -Token or configure requests/http-client.private.env.json env '$EnvName')"
  }

  $BaseUrl = "http://127.0.0.1:$Port"
}

$mcpUrl = "$BaseUrl/mcp"
$healthUrl = "$BaseUrl/health"

$headers = @{
  Accept = "application/json, text/event-stream"
  "Content-Type" = "application/json"
}
if ($authEnabled) {
  $headers.Authorization = "Bearer $Token"
}

Write-Host "== MCP JavaFX E2E Smoke ==" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"
Write-Host "TestName: $TestName"
Write-Host "AuthEnabled: $authEnabled"

# 0) health
try {
  $health = Invoke-RestMethod -Method Get -Uri $healthUrl -Headers @{ Accept = 'application/json' } -TimeoutSec $TimeoutSec
} catch {
  Fail "Health check failed: $($_.Exception.Message)"
}

Require ($health.ok -eq $true) "/health did not return ok=true"
Write-Host "Health OK; tools: $($health.tools -join ', ')"

# 1) initialize
$init = @{
  jsonrpc = "2.0"
  id = 1
  method = "initialize"
  params = @{
    protocolVersion = "2025-03-26"
    capabilities = @{}
    clientInfo = @{ name = "e2e-smoke.ps1"; version = "0.0" }
  }
}

$initResp = Post-Mcp $mcpUrl $headers $init
Require ($initResp.result.protocolVersion -eq "2025-03-26") "Unexpected protocolVersion from initialize"

# 2) notifications/initialized
$inited = @{ jsonrpc = "2.0"; method = "notifications/initialized"; params = @{} }
try {
  # Use curl.exe to avoid any interactive parsing/security prompts.
  $initedJson = ($inited | ConvertTo-Json -Depth 16 -Compress)
  $curlArgs = @(
    "-sS",
    "--connect-timeout", "2",
    "--max-time", "$TimeoutSec",
    "-H", "Accept: application/json, text/event-stream",
    "-H", "Content-Type: application/json",
    "-d", $initedJson,
    "$mcpUrl"
  )
  if ($authEnabled) {
    $curlArgs = @("-H", "Authorization: Bearer $Token") + $curlArgs
  }
  & curl.exe @curlArgs | Out-Null
} catch {
  Fail "notifications/initialized failed: $($_.Exception.Message)"
}

# Helper: tools/call
function Call-Tool([int]$id, [string]$name, [hashtable]$arguments) {
  $req = @{
    jsonrpc = "2.0"
    id = $id
    method = "tools/call"
    params = @{ name = $name; arguments = $arguments }
  }
  return Post-Mcp $mcpUrl $headers $req
}

function Get-GreetingSummary() {
  $resp = Call-Tool 299 "ui_query" @{ selector = @{ predicate = @{ idEquals = "greeting" } }; limit = 5 }
  if ($resp.result.isError) {
    return "(ui_query greeting error)"
  }
  $matches = $resp.result.structuredContent.output.matches
  if ($null -eq $matches -or $matches.Count -eq 0) {
    return "(greeting not found)"
  }
  return $matches[0].summary
}

# 3) discover input + submit refs by CSS
$inputResp = Call-Tool 201 "ui_query" @{ selector = @{ css = "#input" }; limit = 1 }
Require (-not $inputResp.result.isError) "ui_query #input returned isError=true"
$inputMatches = $inputResp.result.structuredContent.output.matches
Require ($null -ne $inputMatches -and $inputMatches.Count -gt 0) "#input not found"
$inputPath = $inputMatches[0].ref.path
Require (-not [string]::IsNullOrWhiteSpace($inputPath)) "#input match missing ref.path"
Write-Host "Found #input: $inputPath"

$submitResp = Call-Tool 202 "ui_query" @{ selector = @{ css = "#submitBtn" }; limit = 1 }
Require (-not $submitResp.result.isError) "ui_query #submitBtn returned isError=true"
$submitMatches = $submitResp.result.structuredContent.output.matches
Require ($null -ne $submitMatches -and $submitMatches.Count -gt 0) "#submitBtn not found"
$submitPath = $submitMatches[0].ref.path
Require (-not [string]::IsNullOrWhiteSpace($submitPath)) "#submitBtn match missing ref.path"
Write-Host "Found #submitBtn: $submitPath"

# 4) set input text (deterministic; no focus/typeText)
$setTextArgs = @{
  awaitUiIdle = $true
  actions = @(
    @{ type = "setText"; target = @{ ref = @{ path = $inputPath } }; text = $TestName }
  )
}
$setTextResp = Call-Tool 203 "ui_perform" $setTextArgs
Require (-not $setTextResp.result.isError) "ui_perform(setText) returned isError=true"

$setResults = $setTextResp.result.structuredContent.output.results
Require ($null -ne $setResults -and $setResults.Count -gt 0) "ui_perform(setText) returned no results"
foreach ($r in $setResults) {
  if ($r.ok -ne $true) {
    Fail "ui_perform(setText) failed: type=$($r.type) error=$($r.error)"
  }
}

# 5) assert input actually contains TestName before clicking
$inputAssert = Call-Tool 204 "ui_query" @{ selector = @{ predicate = @{ idEquals = "input"; textContains = $TestName } }; limit = 5 }
Require (-not $inputAssert.result.isError) "ui_query input-assert returned isError=true"
$inputAssertMatches = $inputAssert.result.structuredContent.output.matches
Require ($null -ne $inputAssertMatches -and $inputAssertMatches.Count -gt 0) "Input text was not set to '$TestName'"

# 6) click submit
$clickArgs = @{
  awaitUiIdle = $true
  actions = @(
    @{ type = "click"; target = @{ ref = @{ path = $submitPath } } }
  )
}
$clickResp = Call-Tool 205 "ui_perform" $clickArgs
Require (-not $clickResp.result.isError) "ui_perform(click) returned isError=true"

$clickResults = $clickResp.result.structuredContent.output.results
Require ($null -ne $clickResults -and $clickResults.Count -gt 0) "ui_perform(click) returned no results"
foreach ($r in $clickResults) {
  if ($r.ok -ne $true) {
    $greetingNow = Get-GreetingSummary
    Fail "ui_perform(click) failed: type=$($r.type) error=$($r.error) | greeting now: $greetingNow"
  }
}

# 7) assert greeting updated
$expected = "Hello, $TestName!"
$greetResp = Call-Tool 206 "ui_query" @{ selector = @{ predicate = @{ idEquals = "greeting"; textContains = $expected } }; limit = 5 }
Require (-not $greetResp.result.isError) "ui_query greeting returned isError=true"
$greetMatches = $greetResp.result.structuredContent.output.matches
if ($null -eq $greetMatches -or $greetMatches.Count -eq 0) {
  $greetingNow = Get-GreetingSummary
  Fail "Greeting label did not update (expected contains '$expected'). Current: $greetingNow"
}

Write-Host "PASS: Greeting updated to include '$expected'" -ForegroundColor Green
exit 0

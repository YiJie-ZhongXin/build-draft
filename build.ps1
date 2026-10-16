param([string[]]$Tasks = @('build'))
$ErrorActionPreference = 'Stop'

# Gradle/JDK on Windows can misread non-ASCII paths in worker argument files.
# Use stable junctions in the user's temporary directory, without moving sources.
$projectRoot = $PSScriptRoot
$hashProvider = [System.Security.Cryptography.SHA256]::Create()
try { $projectHash = ([BitConverter]::ToString($hashProvider.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRoot)))).Replace('-', '').Substring(0, 12).ToLowerInvariant() }
finally { $hashProvider.Dispose() }
$projectAlias = Join-Path $env:TEMP "builddraft-$projectHash"
if (Test-Path -LiteralPath $projectAlias) {
    $existing = Get-Item -LiteralPath $projectAlias -Force
    if ($existing.LinkType -ne 'Junction' -or [string]$existing.Target -ne $projectRoot) {
        throw "Temporary project path belongs to something else: $projectAlias"
    }
} else { New-Item -ItemType Junction -Path $projectAlias -Target $projectRoot | Out-Null }

if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
    $env:GRADLE_USER_HOME = Join-Path $env:TEMP 'builddraft-gradle-home'
}
if ($env:GRADLE_USER_HOME -match '[^\x00-\x7F]' -or $projectAlias -match '[^\x00-\x7F]') {
    throw 'Set TEMP and GRADLE_USER_HOME to ASCII-only paths before building.'
}
& (Join-Path $projectAlias 'gradlew.bat') '-p' $projectAlias @Tasks '--console=plain'
exit $LASTEXITCODE
